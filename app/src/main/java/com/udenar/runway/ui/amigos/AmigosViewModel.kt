package com.udenar.runway.ui.amigos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.amigos.Amigo
import com.udenar.runway.data.amigos.RepositorioAmigos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AmigosEstado(
    val cargando: Boolean = true,
    val error: String? = null,
    val lista: List<Amigo> = emptyList(),
    val codigo: String = "",
    val enviando: Boolean = false,
    val mensaje: String? = null,
    val mensajeExito: Boolean = false,
) {
    val recibidas get() = lista.filter { !it.aceptada && !it.laEnvieYo }
    val enviadas get() = lista.filter { !it.aceptada && it.laEnvieYo }
    val amigos get() = lista.filter { it.aceptada }
    val codigoValido get() = codigo.length == 6
}

class AmigosViewModel(private val repo: RepositorioAmigos) : ViewModel() {

    private val _estado = MutableStateFlow(AmigosEstado())
    val estado: StateFlow<AmigosEstado> = _estado.asStateFlow()

    init {
        recargar()
    }

    fun recargar() {
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            val r = runCatching { repo.lista() }
            _estado.update {
                it.copy(
                    cargando = false,
                    lista = r.getOrDefault(it.lista),
                    error = if (r.isFailure) "No se pudo cargar tu lista de amigos. Revisa tu conexión." else null,
                )
            }
        }
    }

    /** Solo letras y números, en mayúsculas, máximo 6 (formato del código). */
    fun onCodigo(v: String) = _estado.update {
        it.copy(codigo = v.uppercase().filter { c -> c.isLetterOrDigit() }.take(6), mensaje = null)
    }

    fun agregar() {
        val e = _estado.value
        if (!e.codigoValido || e.enviando) return
        _estado.update { it.copy(enviando = true, mensaje = null) }
        viewModelScope.launch {
            val r = runCatching { repo.solicitar(e.codigo) }
            _estado.update {
                it.copy(
                    enviando = false,
                    mensaje = r.getOrElse { "Sin conexión. Intenta de nuevo cuando tengas internet." },
                    mensajeExito = r.getOrNull()?.let { m -> m.startsWith("Solicitud enviada") || m.startsWith("¡Ahora") } == true,
                    codigo = if (r.isSuccess) "" else it.codigo,
                )
            }
            recargar()
        }
    }

    fun responder(a: Amigo, aceptar: Boolean) = accion { repo.responder(a.amistadId, aceptar) }
    fun eliminar(a: Amigo) = accion { repo.eliminar(a.amistadId) }

    private fun accion(bloque: suspend () -> Unit) {
        viewModelScope.launch {
            val r = runCatching { bloque() }
            if (r.isFailure) _estado.update { it.copy(mensaje = "No se pudo completar. Revisa tu conexión.", mensajeExito = false) }
            recargar()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AmigosViewModel((this[APPLICATION_KEY] as RunwayApp).contenedor.amigos) }
        }
    }
}
