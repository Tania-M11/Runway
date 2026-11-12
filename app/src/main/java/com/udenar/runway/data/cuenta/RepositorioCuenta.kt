package com.udenar.runway.data.cuenta

import com.udenar.runway.data.preferencias.PreferenciasPerfil
import com.udenar.runway.data.sync.Sincronizador
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.PerfilCorredor
import com.udenar.runway.domain.PreferenciaDesnivel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException

/** Resultado de registrarse o iniciar sesión, con mensajes listos para mostrar. */
sealed interface ResultadoCuenta {
    data object Ok : ResultadoCuenta
    /** El proyecto exige confirmar el correo antes de entrar. */
    data object ConfirmarCorreo : ResultadoCuenta
    data class Error(val mensaje: String) : ResultadoCuenta
}

/** Fila de la tabla `perfiles` en Supabase. */
@Serializable
data class PerfilRemoto(
    val id: String,
    val nombre: String,
    val avatar: String,
    val codigo: String,
    val nivel: String? = null,
    val desnivel: String? = null,
)

/**
 * Cuentas con Supabase Auth (correo y contraseña) y perfil en la tabla `perfiles`.
 * El perfil se crea solo en el servidor (trigger) con el nombre y avatar del registro.
 */
class RepositorioCuenta(
    private val supabase: SupabaseClient,
    private val perfiles: PreferenciasPerfil,
    private val sincronizador: Sincronizador,
    /** Borra la base local (Room) cuando entra una cuenta distinta a la dueña de esos datos. */
    private val borrarDatosLocales: suspend () -> Unit,
) {
    val estadoSesion: StateFlow<SessionStatus> get() = supabase.auth.sessionStatus

    suspend fun registrar(correo: String, contrasena: String, nombre: String, avatar: String): ResultadoCuenta = intentar {
        supabase.auth.signUpWith(Email) {
            email = correo.trim()
            password = contrasena
            data = buildJsonObject {
                put("nombre", nombre.trim())
                put("avatar", avatar)
            }
        }
        if (supabase.auth.currentSessionOrNull() == null) ResultadoCuenta.ConfirmarCorreo else ResultadoCuenta.Ok
    }

    suspend fun iniciarSesion(correo: String, contrasena: String): ResultadoCuenta = intentar {
        supabase.auth.signInWith(Email) {
            email = correo.trim()
            password = contrasena
        }
        ResultadoCuenta.Ok
    }

    /**
     * Tras iniciar sesión: trae el perfil del servidor a la copia local. Si los datos del
     * teléfono eran de otra cuenta, se borran para no mezclarlos.
     */
    suspend fun prepararSesion(): Boolean {
        val usuario = supabase.auth.currentUserOrNull() ?: return false
        val remoto = runCatching {
            supabase.from("perfiles").select { filter { eq("id", usuario.id) } }.decodeSingleOrNull<PerfilRemoto>()
        }.getOrNull()
        val local = perfiles.perfil.value
        // Cambios del perfil hechos sin conexión: se suben antes de leer el del servidor
        if (remoto != null && perfiles.perfilPendiente && local?.usuarioId == usuario.id) {
            if (subirPerfil(local)) {
                perfiles.perfilPendiente = false
                return true
            }
        }
        if (remoto == null) {
            // Sin red: sirve la copia local si es de esta misma cuenta
            return local?.usuarioId == usuario.id
        }
        val dueno = perfiles.propietarioDatosLocales
        if (dueno != null && dueno != usuario.id) borrarDatosLocales()
        perfiles.propietarioDatosLocales = usuario.id
        // Teléfono nuevo para esta cuenta: se trae su historial desde Supabase
        runCatching { sincronizador.descargarSiEstaVacio() }
        perfiles.guardar(
            PerfilCorredor(
                usuarioId = remoto.id,
                nombre = remoto.nombre,
                avatar = remoto.avatar,
                codigo = remoto.codigo,
                nivel = remoto.nivel?.let { runCatching { Nivel.valueOf(it) }.getOrNull() },
                desnivel = remoto.desnivel?.let { runCatching { PreferenciaDesnivel.valueOf(it) }.getOrNull() },
            ),
        )
        return true
    }

    /**
     * Edición completa del perfil (especificación 5.12). Se guarda primero en el teléfono;
     * si no hay red, queda marcado y se sube en el próximo inicio con conexión.
     * Devuelve true si ya quedó también en Supabase.
     */
    suspend fun editarPerfil(nombre: String, avatar: String, nivel: Nivel, desnivel: PreferenciaDesnivel): Boolean {
        perfiles.actualizar { it.copy(nombre = nombre.trim(), avatar = avatar, nivel = nivel, desnivel = desnivel) }
        val actual = perfiles.perfil.value ?: return false
        val subido = subirPerfil(actual)
        perfiles.perfilPendiente = !subido
        return subido
    }

    private suspend fun subirPerfil(p: PerfilCorredor): Boolean {
        val id = supabase.auth.currentUserOrNull()?.id ?: return false
        return runCatching {
            supabase.from("perfiles").update(buildJsonObject {
                put("nombre", p.nombre)
                put("avatar", p.avatar)
                p.nivel?.let { put("nivel", it.name) }
                p.desnivel?.let { put("desnivel", it.name) }
            }) { filter { eq("id", id) } }
        }.isSuccess
    }

    /** Guarda nivel y desnivel en el teléfono y, si hay red, en Supabase. */
    suspend fun guardarPreferencias(nivel: Nivel, desnivel: PreferenciaDesnivel) {
        perfiles.actualizar { it.copy(nivel = nivel, desnivel = desnivel) }
        actualizarRemoto(buildJsonObject {
            put("nivel", nivel.name)
            put("desnivel", desnivel.name)
        })
    }

    suspend fun actualizarPerfil(nombre: String, avatar: String) {
        perfiles.actualizar { it.copy(nombre = nombre.trim(), avatar = avatar) }
        actualizarRemoto(buildJsonObject {
            put("nombre", nombre.trim())
            put("avatar", avatar)
        })
    }

    private suspend fun actualizarRemoto(cambios: kotlinx.serialization.json.JsonObject) {
        val id = supabase.auth.currentUserOrNull()?.id ?: return
        runCatching { supabase.from("perfiles").update(cambios) { filter { eq("id", id) } } }
    }

    fun correoActual(): String? = supabase.auth.currentUserOrNull()?.email

    /**
     * Antes de salir se intenta subir lo pendiente (con la sesión todavía activa).
     * Devuelve cuántos elementos quedaron sin sincronizar (p. ej. por falta de red).
     */
    suspend fun subirAntesDeSalir(): Int {
        runCatching { sincronizador.subir() }
        return sincronizador.pendientes.first()
    }

    suspend fun cerrarSesion() {
        runCatching { supabase.auth.signOut() }
        perfiles.borrar()
    }

    private suspend fun intentar(bloque: suspend () -> ResultadoCuenta): ResultadoCuenta = try {
        bloque()
    } catch (e: AuthRestException) {
        ResultadoCuenta.Error(
            when (e.errorCode) {
                AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> "Ya existe una cuenta con ese correo. Inicia sesión."
                AuthErrorCode.InvalidCredentials -> "Correo o contraseña incorrectos."
                AuthErrorCode.EmailNotConfirmed -> "Confirma tu correo antes de iniciar sesión."
                AuthErrorCode.WeakPassword -> "La contraseña es muy débil."
                AuthErrorCode.EmailAddressInvalid -> "Ese correo no es válido."
                AuthErrorCode.OverEmailSendRateLimit, AuthErrorCode.OverRequestRateLimit -> "Demasiados intentos. Espera un momento."
                else -> e.description ?: "No se pudo completar. Intenta de nuevo."
            },
        )
    } catch (e: IOException) {
        ResultadoCuenta.Error("Sin conexión. Revisa tu internet e intenta de nuevo.")
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        ResultadoCuenta.Error(
            if (e.javaClass.simpleName.contains("HttpRequest")) "Sin conexión. Revisa tu internet e intenta de nuevo."
            else "No se pudo completar: ${e.message ?: "error desconocido"}",
        )
    }
}

/** Validaciones del formulario (también se prueban en tests). */
object ValidacionCuenta {
    private val CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun correo(c: String): String? = if (CORREO.matches(c.trim())) null else "Escribe un correo válido"

    fun contrasena(c: String): String? = when {
        c.length < 8 -> "Mínimo 8 caracteres"
        c.none { it.isLetter() } || c.none { it.isDigit() } -> "Usa letras y números"
        else -> null
    }

    fun nombre(n: String): String? = when {
        n.isBlank() -> "Escribe tu nombre o alias"
        n.trim().length > 30 -> "Máximo 30 caracteres"
        else -> null
    }
}
