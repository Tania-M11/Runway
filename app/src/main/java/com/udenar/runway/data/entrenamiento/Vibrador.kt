package com.udenar.runway.data.entrenamiento

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Avisos por vibración durante el entrenamiento (se sienten con el teléfono en el bolsillo). */
class Vibrador(context: Context) {

    private val vibrador: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** Tres pulsos largos: te saliste de la ruta. */
    fun desvio() = patron(longArrayOf(0, 400, 200, 400, 200, 400))

    /** Un pulso corto: volviste a la ruta. */
    fun regreso() = patron(longArrayOf(0, 120))

    /** Pulsos de celebración: llegaste al final de la ruta. */
    fun llegada() = patron(longArrayOf(0, 150, 100, 150, 100, 600))

    private fun patron(tiempos: LongArray) {
        val v = vibrador ?: return
        if (!v.hasVibrator()) return
        runCatching { v.vibrate(VibrationEffect.createWaveform(tiempos, -1)) }
    }
}
