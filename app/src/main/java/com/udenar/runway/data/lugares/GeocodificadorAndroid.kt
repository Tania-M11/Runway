package com.udenar.runway.data.lugares

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Lugar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Geocodificador del propio Android (`android.location.Geocoder`).
 *
 * Es parte del mismo paquete `android.location` que da acceso al GPS: además de la posición,
 * el sistema puede convertir un nombre en coordenadas y coordenadas en una dirección.
 * RUNWAY lo usa como respaldo cuando el servicio Photon no responde.
 */
class GeocodificadorAndroid(private val context: Context) {

    val disponible: Boolean get() = Geocoder.isPresent()

    private fun geocoder() = Geocoder(context, Locale.forLanguageTag("es"))

    /** Busca por nombre, limitado a un recuadro de ~50 km alrededor de [cerca] si se conoce. */
    suspend fun buscar(texto: String, cerca: Coordenada?): List<Lugar> {
        if (!disponible) return emptyList()
        val direcciones = withTimeoutOrNull(10_000) {
            withContext(Dispatchers.IO) {
                val g = geocoder()
                val d = 0.5 // grados ≈ 55 km
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        val escucha = object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.toList())
                            override fun onError(errorMessage: String?) = cont.resume(emptyList())
                        }
                        if (cerca != null) g.getFromLocationName(texto, 8, cerca.lat - d, cerca.lon - d, cerca.lat + d, cerca.lon + d, escucha)
                        else g.getFromLocationName(texto, 8, escucha)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    runCatching {
                        if (cerca != null) g.getFromLocationName(texto, 8, cerca.lat - d, cerca.lon - d, cerca.lat + d, cerca.lon + d)
                        else g.getFromLocationName(texto, 8)
                    }.getOrNull().orEmpty()
                }
            }
        }.orEmpty()
        return direcciones.mapNotNull { it.aLugar() }.distinctBy { it.nombre to it.detalle }
    }

    /** Dirección legible de una coordenada (geocodificación inversa). */
    suspend fun nombreDe(c: Coordenada): Lugar? {
        if (!disponible) return null
        val direccion = withTimeoutOrNull(8_000) {
            withContext(Dispatchers.IO) {
                val g = geocoder()
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        g.getFromLocation(c.lat, c.lon, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                            override fun onError(errorMessage: String?) = cont.resume(null)
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    runCatching { g.getFromLocation(c.lat, c.lon, 1)?.firstOrNull() }.getOrNull()
                }
            }
        }
        return direccion?.aLugar()?.copy(coordenada = c)
    }
}

private fun Address.aLugar(): Lugar? {
    if (!hasLatitude() || !hasLongitude()) return null
    val calle = listOfNotNull(thoroughfare, subThoroughfare).joinToString(" ").ifBlank { null }
    // featureName suele ser el nombre del sitio (o solo el número de la casa)
    val sitio = featureName?.takeIf { it != subThoroughfare && it.any { c -> c.isLetter() } }
    val nombre = sitio ?: calle ?: locality ?: subAdminArea ?: adminArea ?: return null
    val detalle = listOfNotNull(calle.takeIf { sitio != null }, locality, adminArea, countryName)
        .filter { it != nombre }.distinct().joinToString(", ")
    return Lugar(nombre, detalle, Coordenada(latitude, longitude))
}
