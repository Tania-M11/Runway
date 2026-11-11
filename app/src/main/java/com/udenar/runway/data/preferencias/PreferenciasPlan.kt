package com.udenar.runway.data.preferencias

import android.content.Context
import androidx.core.content.edit
import com.udenar.runway.domain.Modalidad

/** Recuerda la última distancia y modalidad para no pedirlas de nuevo (especificación 5.4). */
class PreferenciasPlan(context: Context) {

    private val prefs = context.getSharedPreferences("plan", Context.MODE_PRIVATE)

    fun distanciaKm(): Double? = if (prefs.contains(DISTANCIA)) Double.fromBits(prefs.getLong(DISTANCIA, 0)) else null

    fun modalidad(): Modalidad =
        prefs.getString(MODALIDAD, null)?.let { runCatching { Modalidad.valueOf(it) }.getOrNull() } ?: Modalidad.Circular

    fun guardar(distanciaKm: Double, modalidad: Modalidad) = prefs.edit {
        putLong(DISTANCIA, distanciaKm.toRawBits())
        putString(MODALIDAD, modalidad.name)
    }

    private companion object {
        const val DISTANCIA = "distancia"
        const val MODALIDAD = "modalidad"
    }
}
