package com.udenar.runway.data.ubicacion

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Satélites que el receptor del teléfono está viendo y usando para calcular la posición. */
data class EstadoSatelites(
    val visibles: Int,
    val usados: Int,
    /** Satélites usados por sistema: "GPS" → 6, "Galileo" → 3… */
    val porSistema: Map<String, Int>,
)

/**
 * Estado del receptor GNSS (`android.location.GnssStatus`): acceso directo al hardware de
 * geolocalización. Muestra que la posición no "aparece sola": el chip calcula la ubicación
 * con las señales de varios satélites de distintas constelaciones.
 */
class Satelites(context: Context) {

    private val lm = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    fun estado(): Flow<EstadoSatelites> = callbackFlow {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val usados = (0 until status.satelliteCount).filter { status.usedInFix(it) }
                trySend(
                    EstadoSatelites(
                        visibles = status.satelliteCount,
                        usados = usados.size,
                        porSistema = usados.groupingBy { nombreSistema(status.getConstellationType(it)) }.eachCount(),
                    ),
                )
            }
        }
        val registrado = runCatching { lm.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper())) }.getOrDefault(false)
        if (!registrado) close()
        awaitClose { lm.unregisterGnssStatusCallback(callback) }
    }

    private fun nombreSistema(tipo: Int) = when (tipo) {
        GnssStatus.CONSTELLATION_GPS -> "GPS"
        GnssStatus.CONSTELLATION_GALILEO -> "Galileo"
        GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
        GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou"
        GnssStatus.CONSTELLATION_QZSS -> "QZSS"
        GnssStatus.CONSTELLATION_SBAS -> "SBAS"
        else -> "Otro"
    }
}
