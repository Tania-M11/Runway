package com.udenar.runway.ui.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.cuenta.RepositorioCuenta
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.PreferenciaDesnivel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pasos cortos de la configuración inicial (el nombre y el avatar ya vienen del registro). */
enum class PasoConfiguracion { Nivel, Desnivel, Ubicacion }

data class ConfiguracionEstado(
    val paso: PasoConfiguracion = PasoConfiguracion.Nivel,
    val nivel: Nivel? = null,
    val desnivel: PreferenciaDesnivel? = null,
) {
    val puedeContinuar: Boolean
        get() = when (paso) {
            PasoConfiguracion.Nivel -> nivel != null
            PasoConfiguracion.Desnivel -> desnivel != null
            PasoConfiguracion.Ubicacion -> true
        }
}

class ConfiguracionViewModel(private val cuenta: RepositorioCuenta) : ViewModel() {

    private val _estado = MutableStateFlow(ConfiguracionEstado())
    val estado: StateFlow<ConfiguracionEstado> = _estado.asStateFlow()

    fun onNivel(nivel: Nivel) = _estado.update { it.copy(nivel = nivel) }
    fun onDesnivel(desnivel: PreferenciaDesnivel) = _estado.update { it.copy(desnivel = desnivel) }

    fun siguiente() = _estado.update { e ->
        if (!e.puedeContinuar) e
        else e.copy(paso = PasoConfiguracion.entries.getOrElse(e.paso.ordinal + 1) { e.paso })
    }

    /** Devuelve false si ya está en el primer paso. */
    fun anterior(): Boolean {
        val e = _estado.value
        if (e.paso.ordinal == 0) return false
        _estado.update { it.copy(paso = PasoConfiguracion.entries[e.paso.ordinal - 1]) }
        return true
    }

    /** Guarda nivel y desnivel (en el teléfono y en Supabase); la raíz pasa sola a Inicio. */
    fun terminar() {
        val e = _estado.value
        val nivel = e.nivel ?: return
        val desnivel = e.desnivel ?: return
        viewModelScope.launch { cuenta.guardarPreferencias(nivel, desnivel) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ConfiguracionViewModel((this[APPLICATION_KEY] as RunwayApp).contenedor.cuenta) }
        }
    }
}
