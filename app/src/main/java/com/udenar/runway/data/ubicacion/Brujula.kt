package com.udenar.runway.data.ubicacion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Brújula del teléfono (mecanismo 1: hardware). Combina acelerómetro, magnetómetro y giroscopio
 * mediante el sensor virtual de rotación y entrega hacia dónde apunta el teléfono (0° = norte).
 * Se usa para orientar el mapa cuando el corredor está quieto; en movimiento manda el rumbo del GPS.
 */
class Brujula(context: Context) {

    private val sensores = context.getSystemService(SensorManager::class.java)
    private val rotacion: Sensor? = sensores?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val disponible: Boolean get() = rotacion != null

    fun rumbo(): Flow<Float> {
        val sensor = rotacion ?: return emptyFlow()
        return callbackFlow {
            val matriz = FloatArray(9)
            val orientacion = FloatArray(3)
            var suavizado: Float? = null
            val escucha = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(matriz, e.values)
                    SensorManager.getOrientation(matriz, orientacion)
                    val grados = ((Math.toDegrees(orientacion[0].toDouble()) + 360) % 360).toFloat()
                    // Suavizado por el camino corto del círculo (para que 359° → 1° no dé la vuelta)
                    val anterior = suavizado
                    suavizado = if (anterior == null) grados else {
                        val diferencia = ((grados - anterior + 540) % 360) - 180
                        (anterior + diferencia * 0.15f + 360) % 360
                    }
                    trySend(suavizado!!)
                }

                override fun onAccuracyChanged(s: Sensor?, precision: Int) = Unit
            }
            sensores.registerListener(escucha, sensor, SensorManager.SENSOR_DELAY_UI)
            awaitClose { sensores.unregisterListener(escucha) }
        }
    }
}
