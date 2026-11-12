package com.udenar.runway.ui.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.cuenta.RepositorioCuenta
import com.udenar.runway.data.cuenta.ResultadoCuenta
import com.udenar.runway.data.cuenta.ValidacionCuenta
import com.udenar.runway.domain.AVATARES
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PantallaAcceso { Bienvenida, Registro, Ingreso }

data class AccesoEstado(
    val pantalla: PantallaAcceso = PantallaAcceso.Bienvenida,
    val nombre: String = "",
    val avatar: String = AVATARES.first(),
    val correo: String = "",
    val contrasena: String = "",
    /** Los errores de cada campo se muestran después del primer intento de enviar. */
    val mostrarErrores: Boolean = false,
    val enviando: Boolean = false,
    val mensaje: String? = null,
) {
    val errorNombre get() = if (mostrarErrores && pantalla == PantallaAcceso.Registro) ValidacionCuenta.nombre(nombre) else null
    val errorCorreo get() = if (mostrarErrores) ValidacionCuenta.correo(correo) else null
    val errorContrasena
        get() = if (!mostrarErrores) null
        else if (pantalla == PantallaAcceso.Registro) ValidacionCuenta.contrasena(contrasena)
        else if (contrasena.isEmpty()) "Escribe tu contraseña" else null
}

/** Bienvenida, registro e inicio de sesión. Al entrar, la raíz de la app cambia sola de pantalla. */
class AccesoViewModel(private val cuenta: RepositorioCuenta) : ViewModel() {

    private val _estado = MutableStateFlow(AccesoEstado())
    val estado: StateFlow<AccesoEstado> = _estado.asStateFlow()

    fun ir(p: PantallaAcceso) = _estado.update { it.copy(pantalla = p, mostrarErrores = false, mensaje = null) }
    fun onNombre(v: String) = _estado.update { it.copy(nombre = v.take(30), mensaje = null) }
    fun onAvatar(v: String) = _estado.update { it.copy(avatar = v) }
    fun onCorreo(v: String) = _estado.update { it.copy(correo = v.trim(), mensaje = null) }
    fun onContrasena(v: String) = _estado.update { it.copy(contrasena = v, mensaje = null) }

    fun enviar() {
        _estado.update { it.copy(mostrarErrores = true) }
        val e = _estado.value
        if (e.enviando || e.errorNombre != null || e.errorCorreo != null || e.errorContrasena != null) return
        _estado.update { it.copy(enviando = true, mensaje = null) }
        viewModelScope.launch {
            val r = if (e.pantalla == PantallaAcceso.Registro) cuenta.registrar(e.correo, e.contrasena, e.nombre, e.avatar)
            else cuenta.iniciarSesion(e.correo, e.contrasena)
            _estado.update {
                it.copy(
                    enviando = false,
                    mensaje = when (r) {
                        ResultadoCuenta.Ok -> null
                        ResultadoCuenta.ConfirmarCorreo -> "Te enviamos un correo para confirmar tu cuenta. Ábrelo y luego inicia sesión."
                        is ResultadoCuenta.Error -> r.mensaje
                    },
                    pantalla = if (r == ResultadoCuenta.ConfirmarCorreo) PantallaAcceso.Ingreso else it.pantalla,
                )
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AccesoViewModel((this[APPLICATION_KEY] as RunwayApp).contenedor.cuenta) }
        }
    }
}
