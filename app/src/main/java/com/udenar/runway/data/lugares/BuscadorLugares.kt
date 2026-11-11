package com.udenar.runway.data.lugares

import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Lugar
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Buscador de lugares en cualquier parte del mundo con Photon (geocodificador
 * gratuito basado en OpenStreetMap). Permite elegir dónde planificar una ruta.
 */
class BuscadorLugares(private val http: HttpClient) {

    /** Busca por texto; [cerca] da prioridad a resultados próximos a lo que se ve en el mapa. */
    suspend fun buscar(texto: String, cerca: Coordenada?): List<Lugar> {
        val respuesta: RespuestaPhoton = http.get("$BASE/api/") {
            parameter("q", texto)
            parameter("limit", 8)
            if (cerca != null) {
                parameter("lat", cerca.lat)
                parameter("lon", cerca.lon)
            }
        }.body()
        return respuesta.aLugares()
    }

    /** Nombre del lugar más cercano a una coordenada (geocodificación inversa). */
    suspend fun nombreDe(punto: Coordenada): Lugar? {
        val respuesta: RespuestaPhoton = http.get("$BASE/reverse") {
            parameter("lat", punto.lat)
            parameter("lon", punto.lon)
        }.body()
        return respuesta.aLugares().firstOrNull()?.copy(coordenada = punto)
    }

    private companion object {
        const val BASE = "https://photon.komoot.io"
    }
}

// --- Formato GeoJSON que devuelve Photon ---

@Serializable
internal data class RespuestaPhoton(val features: List<ElementoPhoton> = emptyList())

@Serializable
internal data class ElementoPhoton(val geometry: GeometriaPhoton, val properties: PropiedadesPhoton)

@Serializable
internal data class GeometriaPhoton(val coordinates: List<Double>)

@Serializable
internal data class PropiedadesPhoton(
    val name: String? = null,
    val street: String? = null,
    @SerialName("housenumber") val numero: String? = null,
    val city: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
)

internal fun RespuestaPhoton.aLugares(): List<Lugar> =
    features.mapNotNull { it.aLugar() }.distinctBy { it.nombre to it.detalle }

internal fun ElementoPhoton.aLugar(): Lugar? {
    val (lon, lat) = geometry.coordinates.takeIf { it.size >= 2 } ?: return null
    val p = properties
    val calle = listOfNotNull(p.street, p.numero).joinToString(" ").ifBlank { null }
    val nombre = p.name ?: calle ?: p.city ?: p.county ?: p.state ?: p.country ?: return null
    val detalle = listOfNotNull(
        calle.takeIf { p.name != null },
        p.city,
        p.state,
        p.country,
    ).filter { it != nombre }.distinct().joinToString(", ")
    return Lugar(nombre, detalle, Coordenada(lat, lon))
}
