package com.udenar.runway.ui.explorar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.amigos.AmigoEnVivo
import com.udenar.runway.data.amigos.AmigosEnVivo
import com.udenar.runway.data.lugares.BuscadorLugares
import com.udenar.runway.data.offline.RepositorioMapasOffline
import com.udenar.runway.domain.ProgresoDescarga
import com.udenar.runway.domain.Teselas
import com.udenar.runway.domain.ZonaOffline
import kotlinx.coroutines.flow.combine
import com.udenar.runway.data.preferencias.PreferenciasMapa
import com.udenar.runway.data.red.MonitorConexion
import com.udenar.runway.data.ubicacion.RepositorioUbicacion
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Lugar
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.PosicionGps
import com.udenar.runway.ui.mapa.ComandoMapa
import com.udenar.runway.ui.ubicacion.ResultadoPermiso
import kotlinx.coroutines.coroutineScope
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExplorarEstado(
    val camara: PosicionCamara,
    val mapaListo: Boolean = false,
    val errorMapa: Boolean = false,
    val intentoCarga: Int = 0,
    val conectado: Boolean = true,
    val consulta: String = "",
    val buscando: Boolean = false,
    val resultados: List<Lugar> = emptyList(),
    val mensajeBusqueda: String? = null,
    val seleccionado: Lugar? = null,
    val permisoUbicacion: Boolean = false,
    val miPosicion: PosicionGps? = null,
    val buscandoMiPosicion: Boolean = false,
    val aviso: AvisoUbicacion? = null,
    /** Amigos que están corriendo ahora (Supabase Realtime). */
    val amigos: List<AmigoEnVivo> = emptyList(),
    /** Mapas descargados y lo que se ve en pantalla (para "Descargar esta zona"). */
    val zonasOffline: List<ZonaOffline> = emptyList(),
    val vista: Pair<Coordenada, Coordenada>? = null,
    val claveDescarga: String? = null,
    val progresoDescarga: ProgresoDescarga? = null,
    val mensajeDescarga: String? = null,
) {
    /** ¿La cámara está sobre una zona descargada? (para el aviso sin conexión) */
    val sobreZonaDescargada: Boolean get() = zonasOffline.any { it.completa && it.contiene(camara.centro) }
    val teselasVista: Long get() = vista?.let { (sw, ne) -> Teselas.contar(sw, ne) } ?: 0
}


enum class AvisoUbicacion { PermisoNegado, PermisoBloqueado, UbicacionApagada }

/**
 * ViewModel de Explorar: recibe los eventos del mapa (toques, movimientos, errores),
 * consulta la capa de datos y publica un estado que la pantalla solo dibuja.
 */
class ExplorarViewModel(
    private val buscador: BuscadorLugares,
    private val preferencias: PreferenciasMapa,
    private val ubicacion: RepositorioUbicacion,
    private val amigosEnVivo: AmigosEnVivo,
    private val mapasOffline: RepositorioMapasOffline,
    monitor: MonitorConexion,
) : ViewModel() {

    private val _estado = MutableStateFlow(
        ExplorarEstado(
            camara = preferencias.ultimaCamara(),
            permisoUbicacion = ubicacion.tienePermiso(),
            miPosicion = ubicacion.ultimaConocida(),
        ),
    )
    val estado: StateFlow<ExplorarEstado> = _estado.asStateFlow()

    private val _comandos = Channel<ComandoMapa>(Channel.BUFFERED)
    val comandos = _comandos.receiveAsFlow()

    private var busqueda: Job? = null
    private var nombrePunto: Job? = null

    /** La primera posición recibida centra el mapa una sola vez (especificación 5.3). */
    private var centradoInicial = false

    init {
        viewModelScope.launch {
            combine(mapasOffline.zonas, mapasOffline.descargas) { z, d -> z to d }.collect { (zonas, descargas) ->
                _estado.update { e -> e.copy(zonasOffline = zonas, progresoDescarga = e.claveDescarga?.let { descargas[it] }) }
            }
        }
        viewModelScope.launch {
            monitor.conectado.collect { conectado ->
                val antes = _estado.value
                _estado.update { it.copy(conectado = conectado) }
                // Si el mapa falló por falta de red, se reintenta solo al volver la conexión
                if (conectado && !antes.conectado && antes.errorMapa) reintentarMapa()
            }
        }
    }

    // --- Eventos del mapa ---

    fun onEstiloCargado() = _estado.update { it.copy(mapaListo = true, errorMapa = false) }

    fun onErrorMapa() = _estado.update { it.copy(errorMapa = !it.mapaListo) }

    fun reintentarMapa() = _estado.update {
        it.copy(errorMapa = false, mapaListo = false, intentoCarga = it.intentoCarga + 1)
    }

    fun onCamaraQuieta(camara: PosicionCamara) {
        _estado.update { it.copy(camara = camara) }
        preferencias.guardarCamara(camara)
    }

    fun onToqueMapa(punto: Coordenada) {
        // Un toque con resultados abiertos solo los cierra
        if (_estado.value.resultados.isNotEmpty() || _estado.value.mensajeBusqueda != null) {
            limpiarBusqueda()
            return
        }
        _estado.update {
            it.copy(seleccionado = Lugar("Punto seleccionado", "Buscando dirección…", punto), aviso = null)
        }
        nombrePunto?.cancel()
        nombrePunto = viewModelScope.launch {
            val lugar = runCatching { buscador.nombreDe(punto) }.getOrNull()
            _estado.update { e ->
                if (e.seleccionado?.coordenada != punto) e
                else e.copy(seleccionado = lugar ?: Lugar("Punto seleccionado", punto.texto(), punto))
            }
        }
    }

    fun quitarSeleccion() {
        nombrePunto?.cancel()
        _estado.update { it.copy(seleccionado = null) }
    }

    // --- Ubicación del usuario (mecanismo 1 -> ViewModel -> mapa) ---

    /** Se llama al volver a la pantalla: el usuario pudo cambiar el permiso en Ajustes. */
    fun actualizarPermiso() {
        val permiso = ubicacion.tienePermiso()
        _estado.update {
            it.copy(
                permisoUbicacion = permiso,
                aviso = if (permiso && it.aviso != AvisoUbicacion.UbicacionApagada) null else it.aviso,
            )
        }
    }

    /**
     * Escucha el GPS mientras la pantalla está visible. La pantalla la ejecuta con
     * repeatOnLifecycle(STARTED): al salir de la app se cancela y el GPS se apaga.
     */
    suspend fun seguirUbicacion() = coroutineScope {
        if (!ubicacion.tienePermiso()) return@coroutineScope
        launch {
            ubicacion.ubicacionActivadaFlow.collect { activa ->
                if (activa) _estado.update { if (it.aviso == AvisoUbicacion.UbicacionApagada) it.copy(aviso = null) else it }
            }
        }
        ubicacion.posiciones().collect { p ->
            val antes = _estado.value
            _estado.update { it.copy(miPosicion = p, buscandoMiPosicion = false) }
            if (!centradoInicial || antes.buscandoMiPosicion) {
                centradoInicial = true
                _comandos.trySend(ComandoMapa.MoverCamara(p.coordenada, max(antes.camara.zoom, 15.5)))
            }
        }
    }

    /** Botón "centrar en mí" (solo se llama con permiso concedido). */
    fun centrarEnMi() {
        val e = _estado.value
        val posicion = e.miPosicion
        when {
            !ubicacion.ubicacionActivada() -> _estado.update { it.copy(aviso = AvisoUbicacion.UbicacionApagada) }
            posicion != null -> {
                centradoInicial = true
                _comandos.trySend(ComandoMapa.MoverCamara(posicion.coordenada, max(e.camara.zoom, 16.0)))
            }
            else -> _estado.update { it.copy(buscandoMiPosicion = true) }
        }
    }

    fun onResultadoPermiso(resultado: ResultadoPermiso) {
        when (resultado) {
            ResultadoPermiso.Concedido -> {
                _estado.update { it.copy(permisoUbicacion = true, aviso = null) }
                centrarEnMi()
            }
            ResultadoPermiso.Denegado -> _estado.update { it.copy(aviso = AvisoUbicacion.PermisoNegado) }
            ResultadoPermiso.DenegadoPermanente -> _estado.update { it.copy(aviso = AvisoUbicacion.PermisoBloqueado) }
        }
    }

    // --- Mapas sin conexión (parte 12) ---

    fun onVista(sw: Coordenada, ne: Coordenada) = _estado.update { it.copy(vista = sw to ne) }

    /** Descarga lo que se ve en pantalla (el motor baja estilo y teselas por su cuenta). */
    fun descargarVista() {
        val (sw, ne) = _estado.value.vista ?: return
        viewModelScope.launch {
            val nombre = runCatching { buscador.nombreDe(_estado.value.camara.centro) }.getOrNull()?.let { l ->
                listOf(l.nombre, l.detalle.substringBefore(",")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            } ?: "Zona descargada"
            val clave = mapasOffline.clave(sw, ne)
            val mensaje = when (val r = mapasOffline.descargar(nombre, sw, ne)) {
                is RepositorioMapasOffline.Resultado.MuyGrande -> "La zona es muy grande. Acércate un poco más."
                RepositorioMapasOffline.Resultado.Iniciada -> null
            }
            _estado.update { it.copy(claveDescarga = if (mensaje == null) clave else it.claveDescarga, mensajeDescarga = mensaje) }
        }
    }

    fun cerrarMensajeDescarga() = _estado.update { it.copy(mensajeDescarga = null) }

    // --- Amigos en vivo (parte 10) ---

    /** Escucha a los amigos que corren mientras la pantalla está visible. */
    suspend fun seguirAmigos() {
        amigosEnVivo.observar().collect { lista -> _estado.update { it.copy(amigos = lista) } }
    }

    fun irAAmigo(a: AmigoEnVivo) {
        centradoInicial = true
        _comandos.trySend(ComandoMapa.MoverCamara(a.coordenada, 16.0))
    }

    // --- Buscador de lugares (cualquier ciudad) ---

    fun onConsulta(texto: String) {
        _estado.update { it.copy(consulta = texto) }
        busqueda?.cancel()
        val limpio = texto.trim()
        if (limpio.length < 3) {
            _estado.update { it.copy(resultados = emptyList(), buscando = false, mensajeBusqueda = null) }
            return
        }
        busqueda = viewModelScope.launch {
            delay(450) // espera a que el usuario deje de escribir (no saturar el servicio)
            _estado.update { it.copy(buscando = true, mensajeBusqueda = null) }
            val resultado = runCatching { buscador.buscar(limpio, cerca = _estado.value.camara.centro) }
            _estado.update { e ->
                resultado.fold(
                    onSuccess = { lista ->
                        e.copy(
                            buscando = false,
                            resultados = lista,
                            mensajeBusqueda = if (lista.isEmpty()) "No encontramos «$limpio». Prueba con otro nombre." else null,
                        )
                    },
                    onFailure = {
                        e.copy(
                            buscando = false,
                            resultados = emptyList(),
                            mensajeBusqueda = if (!e.conectado) "Sin conexión: no se puede buscar ahora."
                            else "No se pudo buscar. Intenta de nuevo.",
                        )
                    },
                )
            }
        }
    }

    fun elegirLugar(lugar: Lugar) {
        busqueda?.cancel()
        centradoInicial = true // no saltar a la posición del GPS después de elegir un lugar
        _estado.update {
            it.copy(seleccionado = lugar, consulta = "", resultados = emptyList(), mensajeBusqueda = null, buscando = false)
        }
        _comandos.trySend(ComandoMapa.MoverCamara(lugar.coordenada, zoom = 15.5))
    }

    fun limpiarBusqueda() {
        busqueda?.cancel()
        _estado.update { it.copy(consulta = "", resultados = emptyList(), mensajeBusqueda = null, buscando = false) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                ExplorarViewModel(c.buscadorLugares, c.preferenciasMapa, c.ubicacion, c.amigosEnVivo, c.mapasOffline, c.monitorConexion)
            }
        }
    }
}
