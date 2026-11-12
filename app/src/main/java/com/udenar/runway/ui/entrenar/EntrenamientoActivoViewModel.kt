package com.udenar.runway.ui.entrenar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.entrenamiento.EstadoSesion
import com.udenar.runway.data.entrenamiento.SesionEntrenamiento
import com.udenar.runway.data.red.MonitorConexion
import com.udenar.runway.data.ubicacion.Brujula
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.EstadoEntrenamiento
import com.udenar.runway.domain.FiltroGps
import com.udenar.runway.domain.distanciaKm
import com.udenar.runway.domain.ritmoSegKm
import com.udenar.runway.ui.mapa.ComandoMapa
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

data class ActivoUi(
    val sesion: EstadoSesion = EstadoSesion(),
    val duracionMs: Long = 0,
    val ritmoPromedioSegKm: Double? = null,
    val gpsDebil: Boolean = false,
    val conectado: Boolean = true,
    val siguiendo: Boolean = true,
    /** Modo guía: el mapa gira hacia donde vas y se inclina en 3D. Si no, norte arriba. */
    val modoGuia: Boolean = true,
)

/** Entrenamiento activo: la sesión vive en la capa de datos; aquí solo se presenta y se controla. */
class EntrenamientoActivoViewModel(
    private val sesion: SesionEntrenamiento,
    private val brujula: Brujula,
    monitor: MonitorConexion,
) : ViewModel() {

    private val siguiendo = MutableStateFlow(true)
    private val modoGuia = MutableStateFlow(true)
    private var rumboBrujula: Float? = null

    /** Reloj de la pantalla: el cronómetro avanza cada segundo. */
    private val reloj = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val ui: StateFlow<ActivoUi> = combine(sesion.estado, reloj, monitor.conectado, siguiendo, modoGuia) { s, ahora, red, sigue, guia ->
        val duracion = s.duracionMs(ahora)
        val activo = s.estado == EstadoEntrenamiento.Activo
        val ultima = s.ultimaPosicion
        ActivoUi(
            sesion = s,
            duracionMs = duracion,
            ritmoPromedioSegKm = ritmoSegKm(duracion, s.distanciaM),
            // GPS débil: sin lecturas hace más de 10 s o con mucho error (se avisa, no se descarta la sesión)
            gpsDebil = activo && (ultima == null || ahora - s.ultimaRecepcionEn > 10_000 || ultima.precisionM > FiltroGps.PRECISION_MAX_M),
            conectado = red,
            siguiendo = sigue,
            modoGuia = guia,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivoUi(sesion = sesion.estado.value))

    private val _comandos = Channel<ComandoMapa>(Channel.CONFLATED)
    val comandosMapa = _comandos.receiveAsFlow()

    // Última orden de cámara enviada (para no repetir órdenes casi iguales)
    private var ultimoCentro: Coordenada? = null
    private var ultimoRumbo = Double.NaN
    private var ultimoModo: Boolean? = null

    init {
        // La cámara sigue al corredor una vez por segundo mientras no arrastre el mapa
        viewModelScope.launch {
            while (true) {
                if (siguiendo.value) moverCamara(forzar = false)
                delay(1_000)
            }
        }
    }

    /** Escucha la brújula mientras la pantalla está visible (orienta el mapa estando quieto). */
    suspend fun escucharBrujula() {
        brujula.rumbo().collect { rumboBrujula = it }
    }

    /**
     * Rumbo del mapa: en movimiento (> 1,2 m/s) el del GPS, que indica hacia dónde se corre;
     * quieto, el de la brújula, que indica hacia dónde mira el teléfono.
     */
    private fun rumboActual(): Double {
        val p = sesion.estado.value.ultimaPosicion
        val enMovimiento = (p?.velocidadMs ?: 0f) > 1.2f && p?.rumboGrados != null
        return (if (enMovimiento) p!!.rumboGrados else rumboBrujula)?.toDouble() ?: 0.0
    }

    private fun moverCamara(forzar: Boolean) {
        val s = sesion.estado.value
        val c = s.ultimaPosicion?.coordenada ?: s.segmentos.flatten().lastOrNull() ?: return
        val guia = modoGuia.value
        val rumbo = if (guia) rumboActual() else 0.0
        val cambioRumbo = if (ultimoRumbo.isNaN()) 360.0 else abs(((rumbo - ultimoRumbo + 540) % 360) - 180)
        val movio = ultimoCentro?.let { distanciaKm(it, c) * 1000 > 2 } ?: true
        if (!forzar && !movio && cambioRumbo < 8 && ultimoModo == guia) return
        ultimoCentro = c
        ultimoRumbo = rumbo
        ultimoModo = guia
        _comandos.trySend(
            ComandoMapa.Seguir(c, zoom = if (guia) 17.0 else 16.5, rumbo = rumbo, inclinacion = if (guia) 50.0 else 0.0),
        )
    }

    fun dejarDeSeguir() {
        siguiendo.value = false
    }

    fun seguir() {
        siguiendo.value = true
        moverCamara(forzar = true)
    }

    fun alternarModoGuia() {
        modoGuia.value = !modoGuia.value
        siguiendo.value = true
        moverCamara(forzar = true)
    }

    /** Al cargar el mapa: seguir al corredor o mostrar la ruta de referencia completa. */
    fun alCargarMapa() {
        val s = sesion.estado.value
        if (s.ultimaPosicion != null || s.segmentos.flatten().isNotEmpty()) moverCamara(forzar = true)
        else s.referencia?.let { _comandos.trySend(ComandoMapa.Encuadrar(it.puntos)) }
    }

    fun pausar() = sesion.pausar()
    fun reanudar() = sesion.reanudar()

    fun finalizar(alTerminar: (String?) -> Unit) = viewModelScope.launch { alTerminar(sesion.finalizar()) }
    fun descartar(alTerminar: () -> Unit) = viewModelScope.launch {
        sesion.descartar()
        alTerminar()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                EntrenamientoActivoViewModel(c.sesion, c.brujula, c.monitorConexion)
            }
        }
    }
}
