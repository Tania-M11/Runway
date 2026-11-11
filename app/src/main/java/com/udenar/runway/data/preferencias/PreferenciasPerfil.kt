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
 * Perfil del corredor guardado en el teléfono. Mientras sea null, la app muestra
 * la configuración inicial. (Con Supabase, en la parte 8, también se sincroniza.)
 */
class PreferenciasPerfil(context: Context) {

    private val prefs = context.getSharedPreferences("perfil", Context.MODE_PRIVATE)

    private val _perfil = MutableStateFlow(leer())
    val perfil: StateFlow<PerfilCorredor?> = _perfil.asStateFlow()

    fun guardar(perfil: PerfilCorredor) {
        prefs.edit {
            putString(NOMBRE, perfil.nombre)
            putString(NIVEL, perfil.nivel.name)
            putString(DESNIVEL, perfil.desnivel.name)
        }
        _perfil.value = perfil
    }

    private fun leer(): PerfilCorredor? {
        val nombre = prefs.getString(NOMBRE, null) ?: return null
        val nivel = prefs.getString(NIVEL, null)?.let { runCatching { Nivel.valueOf(it) }.getOrNull() } ?: return null
        val desnivel = prefs.getString(DESNIVEL, null)
            ?.let { runCatching { PreferenciaDesnivel.valueOf(it) }.getOrNull() } ?: return null
        return PerfilCorredor(nombre, nivel, desnivel)
    }

    private companion object {
        const val NOMBRE = "nombre"
        const val NIVEL = "nivel"
        const val DESNIVEL = "desnivel"
    }
}
