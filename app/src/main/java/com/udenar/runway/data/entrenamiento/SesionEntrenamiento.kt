package com.udenar.runway.data.entrenamiento

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.udenar.runway.data.amigos.PublicadorUbicacion
import com.udenar.runway.data.local.EntrenamientoEntidad
import com.udenar.runway.data.local.EntrenamientosDao
import com.udenar.runway.data.local.PuntoGpsEntidad
import com.udenar.runway.data.ubicacion.RepositorioUbicacion
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.EstadoEntrenamiento
import com.udenar.runway.domain.FiltroGps
import com.udenar.runway.domain.PosicionGps
import com.udenar.runway.domain.RutaReferencia
import com.udenar.runway.domain.codificarPolyline
import com.udenar.runway.domain.decodificarPolyline
import com.udenar.runway.domain.ritmoSegKm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Lo que la pantalla de entrenamiento necesita saber, en vivo. */
data class EstadoSesion(
    val id: String? = null,
    /** null = no hay entrenamiento en curso. */
    val estado: EstadoEntrenamiento? = null,
    val referencia: RutaReferencia? = null,
    val inicioEn: Long = 0,
    val segmentos: List<List<Coordenada>> = emptyList(),
    val distanciaM: Double = 0.0,
    val acumuladoMs: Long = 0,
    val tramoInicioEn: Long? = null,
    val subidaM: Double = 0.0,
    val ritmoActualSegKm: Double? = null,
    val ultimaPosicion: PosicionGps? = null,
    val ultimaRecepcionEn: Long = 0,
    /** La sesión se recuperó tras un cierre inesperado de la app. */
    val recuperada: Boolean = false,
) {
    val enCurso: Boolean get() = estado == EstadoEntrenamiento.Activo || estado == EstadoEntrenamiento.Pausado
    fun duracionMs(ahora: Long): Long = acumuladoMs + (tramoInicioEn?.let { (ahora - it).coerceAtLeast(0) } ?: 0)
}

/**
 * Sesión de entrenamiento (especificación 5.7 y 5.8; plan 5.2).
 *
 * - Escucha el GPS (mecanismo 1) mientras el entrenamiento está activo.
 * - Filtra el ruido ([FiltroGps]) y guarda **cada punto aceptado en SQLite al instante**,
 *   con o sin internet (offline-first).
 * - El [ServicioEntrenamiento] en primer plano mantiene viva la app con la pantalla apagada.
 *
 * Es única para toda la app (vive en el AppContainer), así la pantalla y el servicio
 * comparten el mismo estado.
 */
class SesionEntrenamiento(
    private val app: Context,
    private val ubicacion: RepositorioUbicacion,
    private val dao: EntrenamientosDao,
    /** Comparte la posición con los amigos mientras el entrenamiento está activo (parte 10). */
    private val publicador: PublicadorUbicacion? = null,
    private val reloj: () -> Long = System::currentTimeMillis,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bloqueo = Mutex()

    private val _estado = MutableStateFlow(EstadoSesion())
    val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    private var gps: Job? = null
    private var entidad: EntrenamientoEntidad? = null
    private var ultimaAceptada: PosicionGps? = null
    private var altitudReferencia: Double? = null
    /** (tiempo, distancia acumulada) de los últimos 30 s, para el ritmo actual. */
    private val ventana = ArrayDeque<Pair<Long, Double>>()

    // ------------------------------------------------------------------ Control

    fun iniciar(referencia: RutaReferencia?) {
        if (_estado.value.enCurso) return
        val ahora = reloj()
        val e = EntrenamientoEntidad(
            id = UUID.randomUUID().toString(),
            rutaId = referencia?.rutaId,
            rutaNombre = referencia?.nombre,
            rutaTrazado = referencia?.puntos?.let { codificarPolyline(it) },
            inicioEn = ahora,
            finEn = null,
            estado = ACTIVO,
            distanciaM = 0.0,
            duracionMs = 0,
            tramoInicioEn = ahora,
            segmento = 0,
            subidaM = 0.0,
            trazado = null,
            actualizadoEn = ahora,
        )
        entidad = e
        reiniciarCalculos()
        _estado.value = EstadoSesion(
            id = e.id,
            estado = EstadoEntrenamiento.Activo,
            referencia = referencia,
            inicioEn = ahora,
            segmentos = listOf(emptyList()),
            tramoInicioEn = ahora,
        )
        scope.launch { bloqueo.withLock { dao.insertar(e) } }
        arrancarServicio()
        escucharGps()
    }

    fun pausar() {
        val s = _estado.value
        if (s.estado != EstadoEntrenamiento.Activo) return
        val ahora = reloj()
        val acumulado = s.duracionMs(ahora)
        _estado.update { it.copy(estado = EstadoEntrenamiento.Pausado, acumuladoMs = acumulado, tramoInicioEn = null, ritmoActualSegKm = null) }
        scope.launch { publicador?.retirar() } // en pausa los amigos dejan de verte
        guardarEntidad { it.copy(estado = PAUSADO, duracionMs = acumulado, tramoInicioEn = null, actualizadoEn = ahora) }
    }

    fun reanudar() {
        val s = _estado.value
        if (s.estado != EstadoEntrenamiento.Pausado) return
        val ahora = reloj()
        reiniciarCalculos() // el hueco de la pausa no cuenta como distancia
        _estado.update {
            it.copy(estado = EstadoEntrenamiento.Activo, tramoInicioEn = ahora, segmentos = it.segmentos + listOf(emptyList()), recuperada = false)
        }
        guardarEntidad { it.copy(estado = ACTIVO, tramoInicioEn = ahora, segmento = it.segmento + 1, actualizadoEn = ahora) }
        arrancarServicio()
        escucharGps()
    }

    /** Cierra la sesión, consolida métricas y trazado, y devuelve el id para el resumen. */
    suspend fun finalizar(): String? {
        val s = _estado.value
        if (!s.enCurso) return null
        val ahora = reloj()
        val duracion = s.duracionMs(ahora)
        gps?.cancel()
        val id = s.id
        bloqueo.withLock {
            val e = entidad ?: return null
            val trazado = codificarPolyline(s.segmentos.flatten())
            val final = e.copy(
                estado = FINALIZADO, finEn = ahora, duracionMs = duracion, tramoInicioEn = null,
                distanciaM = s.distanciaM, subidaM = s.subidaM, trazado = trazado, actualizadoEn = ahora,
            )
            dao.actualizar(final)
        }
        terminarSesion()
        return id
    }

    /** Descarta la sesión sin guardarla (se pide confirmación antes en la pantalla). */
    suspend fun descartar() {
        val id = _estado.value.id ?: return
        gps?.cancel()
        bloqueo.withLock { dao.borrar(id) }
        terminarSesion()
    }

    /**
     * Si la app se cerró a mitad de un entrenamiento, lo recupera desde SQLite como pausado.
     * El tiempo cuenta hasta el último punto guardado (no el tiempo que la app estuvo cerrada).
     */
    suspend fun recuperarSiHaceFalta(): Boolean {
        if (_estado.value.enCurso) return false
        val e = bloqueo.withLock { dao.sinTerminar() } ?: return false
        val puntos = dao.puntos(e.id)
        val acumulado = e.duracionMs + (e.tramoInicioEn?.let { (e.actualizadoEn - it).coerceAtLeast(0) } ?: 0)
        val pausada = e.copy(estado = PAUSADO, duracionMs = acumulado, tramoInicioEn = null)
        entidad = pausada
        bloqueo.withLock { dao.actualizar(pausada) }
        reiniciarCalculos()
        val segmentos = puntos.groupBy { it.segmento }.toSortedMap().values.map { t -> t.map { Coordenada(it.lat, it.lon) } }
        _estado.value = EstadoSesion(
            id = e.id,
            estado = EstadoEntrenamiento.Pausado,
            referencia = e.rutaTrazado?.let { RutaReferencia(e.rutaId, e.rutaNombre ?: "Ruta", decodificarPolyline(it)) },
            inicioEn = e.inicioEn,
            segmentos = segmentos.ifEmpty { listOf(emptyList()) },
            distanciaM = e.distanciaM,
            acumuladoMs = acumulado,
            subidaM = e.subidaM,
            recuperada = true,
        )
        return true
    }

    // ------------------------------------------------------------------ GPS

    private fun escucharGps() {
        gps?.cancel()
        gps = scope.launch {
            ubicacion.posiciones(intervaloMs = 1_000).collect { procesar(it) }
        }
    }

    private suspend fun procesar(p: PosicionGps) {
        val ahora = reloj()
        _estado.update { it.copy(ultimaPosicion = p, ultimaRecepcionEn = ahora) }
        val s = _estado.value
        if (s.estado != EstadoEntrenamiento.Activo) return
        val decision = FiltroGps.evaluar(ultimaAceptada, p)
        if (decision !is FiltroGps.Decision.Aceptar) return
        ultimaAceptada = p

        val distancia = s.distanciaM + decision.metros
        var subida = s.subidaM
        // Subida con umbral de 5 m: la altitud del GPS es ruidosa
        p.altitudM?.let { alt ->
            val ref = altitudReferencia
            when {
                ref == null -> altitudReferencia = alt
                alt - ref >= 5 -> {
                    subida += alt - ref
                    altitudReferencia = alt
                }
                alt - ref <= -5 -> altitudReferencia = alt
            }
        }
        // Ritmo actual: distancia y tiempo de los últimos 30 s
        ventana.addLast(p.tiempoMs to distancia)
        while (ventana.size > 1 && p.tiempoMs - ventana.first().first > 30_000) ventana.removeFirst()
        val ritmo = if (ventana.size > 1) {
            ritmoSegKm(p.tiempoMs - ventana.first().first, distancia - ventana.first().second)
                ?.takeIf { distancia - ventana.first().second >= 25 }
        } else null

        _estado.update {
            it.copy(
                distanciaM = distancia,
                subidaM = subida,
                ritmoActualSegKm = ritmo,
                segmentos = it.segmentos.dropLast(1) + listOf(it.segmentos.lastOrNull().orEmpty() + p.coordenada),
            )
        }
        val inicio = _estado.value.inicioEn
        scope.launch { publicador?.publicar(p.coordenada, distancia, inicio) }
        bloqueo.withLock {
            val e = entidad ?: return
            val actualizada = e.copy(distanciaM = distancia, subidaM = subida, actualizadoEn = ahora)
            entidad = actualizada
            dao.registrarPunto(
                PuntoGpsEntidad(
                    entrenamientoId = e.id,
                    segmento = e.segmento,
                    lat = p.coordenada.lat,
                    lon = p.coordenada.lon,
                    altitudM = p.altitudM,
                    precisionM = p.precisionM,
                    velocidadMs = p.velocidadMs,
                    tiempoMs = p.tiempoMs,
                ),
                actualizada,
            )
        }
    }

    // ------------------------------------------------------------------ Auxiliares

    private fun reiniciarCalculos() {
        ultimaAceptada = null
        altitudReferencia = null
        ventana.clear()
    }

    private fun guardarEntidad(cambio: (EntrenamientoEntidad) -> EntrenamientoEntidad) {
        scope.launch {
            bloqueo.withLock {
                val e = entidad ?: return@withLock
                val nueva = cambio(e)
                entidad = nueva
                dao.actualizar(nueva)
            }
        }
    }

    private fun terminarSesion() {
        scope.launch { publicador?.retirar() }
        gps?.cancel()
        entidad = null
        reiniciarCalculos()
        _estado.value = EstadoSesion() // el servicio lo detecta y se detiene
    }

    private fun arrancarServicio() {
        ContextCompat.startForegroundService(app, Intent(app, ServicioEntrenamiento::class.java))
    }

    /** Lo llama el servicio si Android lo reinicia tras matar el proceso. */
    internal suspend fun continuarTrasReinicio(): Boolean {
        if (_estado.value.enCurso) return true
        if (!recuperarSiHaceFalta()) return false
        reanudar()
        return true
    }

    private companion object {
        const val ACTIVO = "ACTIVO"
        const val PAUSADO = "PAUSADO"
        const val FINALIZADO = "FINALIZADO"
    }
}
