package com.udenar.runway.ui.entrenar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.entrenamiento.EstadoSesion
import com.udenar.runway.data.entrenamiento.SesionEntrenamiento
import com.udenar.runway.data.local.RepositorioRutas
import com.udenar.runway.data.ubicacion.RepositorioUbicacion
import com.udenar.runway.domain.FiltroGps
import com.udenar.runway.domain.PosicionGps
import com.udenar.runway.domain.RutaGuardada
import com.udenar.runway.domain.RutaReferencia
import com.udenar.runway.domain.Visibilidad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class EstadoGps { SinPermiso, Apagado, Buscando, Debil, Listo }

data class EntrenarEstado(
    val sesion: EstadoSesion = EstadoSesion(),
    /** Reutilizables primero (son las pensadas para repetir entrenamientos). */
    val rutas: List<RutaGuardada> = emptyList(),
    /** null = carrera libre. */
    val rutaElegida: String? = null,
    val gps: EstadoGps = EstadoGps.Buscando,
    val precisionM: Float? = null,
)

/** Pestaña Entrenar (especificación 4): configuración breve e inicio del seguimiento GPS. */
class EntrenarViewModel(
    val sesion: SesionEntrenamiento,
    private val ubicacion: RepositorioUbicacion,
    rutas: RepositorioRutas,
) : ViewModel() {

    private val eleccion = MutableStateFlow<String?>(null)
    private val gps = MutableStateFlow(EstadoGps.Buscando to null as Float?)

    val estado: StateFlow<EntrenarEstado> = combine(sesion.estado, rutas.rutas(), eleccion, gps) { s, lista, elegida, g ->
        val ordenadas = lista.sortedBy { if (it.visibilidad == Visibilidad.Reutilizable) 0 else 1 }
        EntrenarEstado(
            sesion = s,
            rutas = ordenadas,
            rutaElegida = elegida?.takeIf { id -> lista.any { it.id == id } },
            gps = g.first,
            precisionM = g.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntrenarEstado())

    fun elegir(id: String?) {
        eleccion.value = id
    }

    fun referenciaElegida(): RutaReferencia? {
        val id = eleccion.value ?: return null
        val r = estado.value.rutas.firstOrNull { it.id == id } ?: return null
        return RutaReferencia(r.id, r.nombre, r.puntos)
    }

    /** Estado del GPS mientras la pantalla está visible (para saber si ya hay buena señal). */
    suspend fun vigilarGps() {
        when {
            !ubicacion.tienePermiso() -> gps.value = EstadoGps.SinPermiso to null
            !ubicacion.ubicacionActivada() -> gps.value = EstadoGps.Apagado to null
            sesion.estado.value.enCurso -> Unit // la sesión ya usa el GPS
            else -> {
                gps.value = EstadoGps.Buscando to null
                ubicacion.posiciones().collect { p: PosicionGps ->
                    gps.update {
                        (if (p.deGps && p.precisionM <= FiltroGps.PRECISION_MAX_M) EstadoGps.Listo else EstadoGps.Debil) to p.precisionM
                    }
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                EntrenarViewModel(c.sesion, c.ubicacion, c.rutas)
            }
        }
    }
}
