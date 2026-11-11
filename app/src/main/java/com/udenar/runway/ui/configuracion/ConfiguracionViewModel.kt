package com.udenar.runway.ui.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.preferencias.PreferenciasPerfil
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.PerfilCorredor
import com.udenar.runway.domain.PreferenciaDesnivel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Pasos cortos de la configuración inicial (la especificación pide evitar formularios largos). */
enum class PasoConfiguracion { Nombre, Nivel, Desnivel, Ubicacion }

data class ConfiguracionEstado(
    val paso: PasoConfiguracion = PasoConfiguracion.Nombre,
    val nombre: String = "",
    val nivel: Nivel? = null,
    val desnivel: PreferenciaDesnivel? = null,
) {
    val puedeContinuar: Boolean
        get() = when (paso) {
            PasoConfiguracion.Nombre -> nombre.isNotBlank()
            PasoConfiguracion.Nivel -> nivel != null
            PasoConfiguracion.Desnivel -> desnivel != null
            PasoConfiguracion.Ubicacion -> true
        }
}

const val MAX_NOMBRE = 30

class ConfiguracionViewModel(private val perfiles: PreferenciasPerfil) : ViewModel() {

    private val _estado = MutableStateFlow(ConfiguracionEstado())
    val estado: StateFlow<ConfiguracionEstado> = _estado.asStateFlow()

    fun onNombre(texto: String) = _estado.update { it.copy(nombre = texto.take(MAX_NOMBRE)) }
    fun onNivel(nivel: Nivel) = _estado.update { it.copy(nivel = nivel) }
    fun onDesnivel(desnivel: PreferenciaDesnivel) = _estado.update { it.copy(desnivel = desnivel) }

    fun siguiente() = _estado.update { e ->
        if (!e.puedeContinuar) e
        else e.copy(paso = PasoConfiguracion.entries.getOrElse(e.paso.ordinal + 1) { e.paso })
    }

    /** Devuelve false si ya está en el primer paso (la pantalla deja que Atrás cierre la app). */
    fun anterior(): Boolean {
        val e = _estado.value
        if (e.paso.ordinal == 0) return false
        _estado.update { it.copy(paso = PasoConfiguracion.entries[e.paso.ordinal - 1]) }
        return true
    }

    /** Guarda el perfil; al hacerlo, la raíz de la app pasa automáticamente a Inicio. */
    fun terminar() {
        val e = _estado.value
        val nivel = e.nivel ?: return
        val desnivel = e.desnivel ?: return
        perfiles.guardar(PerfilCorredor(e.nombre.trim(), nivel, desnivel))
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                ConfiguracionViewModel((this[APPLICATION_KEY] as RunwayApp).contenedor.preferenciasPerfil)
            }
        }
    }
}
