package com.udenar.runway.ui.rutas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.local.RepositorioRutas
import com.udenar.runway.data.preferencias.PreferenciasPerfil
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.RutaGuardada
import com.udenar.runway.domain.Visibilidad
import com.udenar.runway.ui.mapa.ComandoMapa
import com.udenar.runway.ui.navegacion.RutaRutaGuardada
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Filtros de Mis rutas (especificación 5.11). null = todas. */
enum class FiltroRutas(val etiqueta: String, val visibilidad: Visibilidad?) {
    Todas("Todas", null),
    Privadas("Privadas", Visibilidad.Privada),
    Reutilizables("Reutilizables", Visibilidad.Reutilizable),
    Compartidas("Compartidas", Visibilidad.Compartida),
}

data class MisRutasEstado(
    val cargando: Boolean = true,
    val filtro: FiltroRutas = FiltroRutas.Todas,
    val todas: List<RutaGuardada> = emptyList(),
) {
    val visibles: List<RutaGuardada>
        get() = filtro.visibilidad?.let { v -> todas.filter { it.visibilidad == v } } ?: todas
}

class MisRutasViewModel(repositorio: RepositorioRutas) : ViewModel() {

    private val filtro = MutableStateFlow(FiltroRutas.Todas)

    val estado: StateFlow<MisRutasEstado> = combine(repositorio.rutas(), filtro) { rutas, f ->
        MisRutasEstado(cargando = false, filtro = f, todas = rutas)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MisRutasEstado())

    fun onFiltro(f: FiltroRutas) {
        filtro.value = f
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MisRutasViewModel((this[APPLICATION_KEY] as RunwayApp).contenedor.rutas) }
        }
    }
}

/** Detalle de una ruta guardada: consultar, renombrar, cambiar visibilidad o eliminar. */
class RutaGuardadaViewModel(
    val id: String,
    private val repositorio: RepositorioRutas,
    perfil: PreferenciasPerfil,
) : ViewModel() {

    val nivel: Nivel = perfil.perfil.value?.nivel ?: Nivel.Intermedio

    /** null mientras carga; también null si fue eliminada (la pantalla vuelve atrás). */
    val ruta: StateFlow<RutaGuardada?> = repositorio.ruta(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _comandos = Channel<ComandoMapa>(Channel.CONFLATED)
    val comandosMapa = _comandos.receiveAsFlow()

    fun encuadrar() {
        ruta.value?.let { _comandos.trySend(ComandoMapa.Encuadrar(it.puntos)) }
    }

    fun renombrar(nombre: String) = viewModelScope.launch { repositorio.renombrar(id, nombre) }

    fun cambiarVisibilidad(v: Visibilidad) = viewModelScope.launch { repositorio.cambiarVisibilidad(id, v) }

    fun eliminar(alTerminar: () -> Unit) = viewModelScope.launch {
        repositorio.eliminar(id)
        alTerminar()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                val id = createSavedStateHandle().toRoute<RutaRutaGuardada>().id
                RutaGuardadaViewModel(id, c.rutas, c.preferenciasPerfil)
            }
        }
    }
}
