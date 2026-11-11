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
import com.udenar.runway.domain.EstadoEntrenamiento
import com.udenar.runway.domain.FiltroGps
import com.udenar.runway.domain.ritmoSegKm
import com.udenar.runway.ui.mapa.ComandoMapa
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActivoUi(
    val sesion: EstadoSesion = EstadoSesion(),
    val duracionMs: Long = 0,
    val ritmoPromedioSegKm: Double? = null,
    val gpsDebil: Boolean = false,
    val conectado: Boolean = true,
    val siguiendo: Boolean = true,
)

/** Entrenamiento activo: la sesión vive en la capa de datos; aquí solo se presenta y se controla. */
class EntrenamientoActivoViewModel(
    private val sesion: SesionEntrenamiento,
    monitor: MonitorConexion,
) : ViewModel() {

    private val siguiendo = MutableStateFlow(true)

    /** Reloj de la pantalla: el cronómetro avanza cada segundo. */
    private val reloj = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val ui: StateFlow<ActivoUi> = combine(sesion.estado, reloj, monitor.conectado, siguiendo) { s, ahora, red, sigue ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivoUi(sesion = sesion.estado.value))

    private val _comandos = Channel<ComandoMapa>(Channel.CONFLATED)
    val comandosMapa = _comandos.receiveAsFlow()

    init {
        // La cámara sigue al corredor mientras no arrastre el mapa
        viewModelScope.launch {
            sesion.estado.map { it.ultimaPosicion?.coordenada }.distinctUntilChanged().collect { c ->
                if (c != null && siguiendo.value) _comandos.trySend(ComandoMapa.MoverCamara(c, 16.5))
            }
        }
    }

    fun dejarDeSeguir() {
        siguiendo.value = false
    }

    fun seguir() {
        siguiendo.value = true
        val s = sesion.estado.value
        val c = s.ultimaPosicion?.coordenada ?: s.segmentos.flatten().lastOrNull()
        if (c != null) _comandos.trySend(ComandoMapa.MoverCamara(c, 16.5))
    }

    /** Al cargar el mapa: centrar en la posición o mostrar la ruta de referencia completa. */
    fun alCargarMapa() {
        val s = sesion.estado.value
        val c = s.ultimaPosicion?.coordenada ?: s.segmentos.flatten().lastOrNull()
        when {
            c != null -> _comandos.trySend(ComandoMapa.MoverCamara(c, 16.5))
            s.referencia != null -> _comandos.trySend(ComandoMapa.Encuadrar(s.referencia.puntos))
        }
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
                EntrenamientoActivoViewModel(c.sesion, c.monitorConexion)
            }
        }
    }
}
