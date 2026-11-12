package com.udenar.runway.data.preferencias

import android.content.Context
import androidx.core.content.edit
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.PerfilCorredor
import com.udenar.runway.domain.PreferenciaDesnivel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Copia local del perfil de la cuenta (para usar la app sin internet).
 * La fuente de verdad es la tabla `perfiles` de Supabase.
 */
class PreferenciasPerfil(context: Context) {

    private val prefs = context.getSharedPreferences("perfil", Context.MODE_PRIVATE)

    private val _perfil = MutableStateFlow(leer())
    val perfil: StateFlow<PerfilCorredor?> = _perfil.asStateFlow()

    fun guardar(perfil: PerfilCorredor) {
        prefs.edit {
            putString(USUARIO, perfil.usuarioId)
            putString(NOMBRE, perfil.nombre)
            putString(AVATAR, perfil.avatar)
            putString(CODIGO, perfil.codigo)
            putString(NIVEL, perfil.nivel?.name)
            putString(DESNIVEL, perfil.desnivel?.name)
        }
        _perfil.value = perfil
    }

    fun actualizar(cambio: (PerfilCorredor) -> PerfilCorredor) {
        _perfil.value?.let { guardar(cambio(it)) }
    }

    /** Al cerrar sesión se olvida el perfil (los datos de Room se conservan hasta que entre otra cuenta). */
    fun borrar() {
        prefs.edit {
            listOf(USUARIO, NOMBRE, AVATAR, CODIGO, NIVEL, DESNIVEL, PENDIENTE).forEach { remove(it) }
        }
        _perfil.value = null
    }

    /** Hay cambios del perfil hechos sin conexión que aún no llegan a Supabase. */
    var perfilPendiente: Boolean
        get() = prefs.getBoolean(PENDIENTE, false)
        set(valor) = prefs.edit { putBoolean(PENDIENTE, valor) }

    /** Cuenta a la que pertenecen los datos guardados en la base local del teléfono. */
    var propietarioDatosLocales: String?
        get() = prefs.getString(PROPIETARIO, null)
        set(valor) = prefs.edit { putString(PROPIETARIO, valor) }

    private fun leer(): PerfilCorredor? {
        val usuario = prefs.getString(USUARIO, null) ?: return null
        return PerfilCorredor(
            usuarioId = usuario,
            nombre = prefs.getString(NOMBRE, null) ?: "Corredor",
            avatar = prefs.getString(AVATAR, null) ?: "avatar_1",
            codigo = prefs.getString(CODIGO, null).orEmpty(),
            nivel = prefs.getString(NIVEL, null)?.let { runCatching { Nivel.valueOf(it) }.getOrNull() },
            desnivel = prefs.getString(DESNIVEL, null)?.let { runCatching { PreferenciaDesnivel.valueOf(it) }.getOrNull() },
        )
    }

    private companion object {
        const val USUARIO = "usuario"
        const val NOMBRE = "nombre"
        const val AVATAR = "avatar"
        const val CODIGO = "codigo"
        const val NIVEL = "nivel"
        const val DESNIVEL = "desnivel"
        const val PROPIETARIO = "propietario_datos_locales"
        const val PENDIENTE = "perfil_pendiente"
    }
}
