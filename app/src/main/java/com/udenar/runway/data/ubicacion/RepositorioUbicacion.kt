package com.udenar.runway.data.ubicacion

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.PosicionGps
import com.udenar.runway.domain.mejorPosicion
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Mecanismo 1: acceso al hardware de geolocalización con `android.location`.
 *
 * Se usa directamente el `LocationManager` del sistema (sin Google Play Services):
 * el proveedor GPS da precisión y el de red da una primera posición rápida.
 * Las pantallas nunca tocan el `LocationManager`; reciben un `Flow<PosicionGps>`.
 */
class RepositorioUbicacion(context: Context) {

    private val app = context.applicationContext
    private val lm = app.getSystemService(LocationManager::class.java)

    fun tienePermiso(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** ¿Está encendida la ubicación del teléfono (ajuste del sistema)? */
    fun ubicacionActivada(): Boolean = LocationManagerCompat.isLocationEnabled(lm)

    /** Emite cada vez que el usuario enciende o apaga la ubicación del teléfono. */
    val ubicacionActivadaFlow: Flow<Boolean> = callbackFlow {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                trySend(ubicacionActivada())
            }
        }
        trySend(ubicacionActivada())
        ContextCompat.registerReceiver(
            app,
            receptor,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { app.unregisterReceiver(receptor) }
    }.distinctUntilChanged()

    /** Última posición que el sistema ya conoce (instantánea, puede ser vieja o nula). */
    @SuppressLint("MissingPermission")
    fun ultimaConocida(): PosicionGps? {
        if (!tienePermiso()) return null
        return proveedores()
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.aPosicion()
    }

    /**
     * Posiciones en vivo mientras alguien recolecte el Flow. Al cancelarse
     * (p. ej. la pantalla pasa a segundo plano) se apaga el GPS para ahorrar batería.
     */
    @SuppressLint("MissingPermission")
    fun posiciones(intervaloMs: Long = 2_000, distanciaMinM: Float = 0f): Flow<PosicionGps> = callbackFlow {
        if (!tienePermiso()) {
            close()
            return@callbackFlow
        }
        var ultima: PosicionGps? = null
        fun publicar(p: PosicionGps) {
            if (mejorPosicion(ultima, p)) {
                ultima = p
                trySend(p)
            }
        }
        // Se implementan todos los métodos: en Android 8-10 no tienen implementación por defecto
        val escucha = object : LocationListener {
            override fun onLocationChanged(location: Location) = publicar(location.aPosicion())
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Requerido en API < 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        ultimaConocida()?.let { publicar(it) }
        proveedores().forEach { proveedor ->
            lm.requestLocationUpdates(proveedor, intervaloMs, distanciaMinM, escucha, Looper.getMainLooper())
        }
        awaitClose { lm.removeUpdates(escucha) }
    }

    private fun proveedores() =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in lm.allProviders }
}

private fun Location.aPosicion() = PosicionGps(
    coordenada = Coordenada(latitude, longitude),
    precisionM = if (hasAccuracy()) accuracy else 9_999f,
    tiempoMs = time,
    altitudM = if (hasAltitude()) altitude else null,
    velocidadMs = if (hasSpeed()) speed else null,
    deGps = provider == LocationManager.GPS_PROVIDER,
)
