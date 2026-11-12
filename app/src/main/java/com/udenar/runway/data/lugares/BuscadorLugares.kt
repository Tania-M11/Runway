package com.udenar.runway.data.lugares

import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Lugar
import com.udenar.runway.domain.LugarSugerido
import com.udenar.runway.domain.distanciaKm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale
import kotlin.math.cos
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Buscador de lugares en cualquier parte del mundo con Photon (geocodificador
 * gratuito basado en OpenStreetMap). Permite elegir dónde planificar una ruta.
 * Si Photon no responde, se usa el geocodificador de Android ([respaldo]).
 */
class BuscadorLugares(private val http: HttpClient, private val respaldo: GeocodificadorAndroid? = null) {

    /** Busca por texto; [cerca] da prioridad a resultados próximos a lo que se ve en el mapa. */
    suspend fun buscar(texto: String, cerca: Coordenada?): List<Lugar> = try {
        buscarEnPhoton(texto, cerca)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        // Photon caído o sin respuesta: se intenta con el geocodificador del sistema
        respaldo?.buscar(texto, cerca)?.takeIf { it.isNotEmpty() } ?: throw e
    }

    private suspend fun buscarEnPhoton(texto: String, cerca: Coordenada?): List<Lugar> {
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
    suspend fun nombreDe(punto: Coordenada): Lugar? =
        runCatching { nombreEnPhoton(punto) }.getOrNull() ?: respaldo?.nombreDe(punto)

    private suspend fun nombreEnPhoton(punto: Coordenada): Lugar? {
        val respuesta: RespuestaPhoton = http.get("$BASE/reverse") {
            parameter("lat", punto.lat)
            parameter("lon", punto.lon)
        }.body()
        return respuesta.aLugares().firstOrNull()?.copy(coordenada = punto)
    }

    /**
     * Lugares para correr hasta ellos alrededor de [centro]: parques, plazas, estadios,
     * miradores y polideportivos de OpenStreetMap, dentro de un recuadro de [radioKm].
     */
    suspend fun lugaresCercanos(centro: Coordenada, radioKm: Double): List<LugarSugerido> = coroutineScope {
        val dLat = radioKm / 111.0
        val dLon = radioKm / (111.0 * cos(Math.toRadians(centro.lat)).coerceAtLeast(0.2))
        val recuadro = String.format(
            Locale.US, "%.6f,%.6f,%.6f,%.6f",
            centro.lon - dLon, centro.lat - dLat, centro.lon + dLon, centro.lat + dLat,
        )
        CATEGORIAS.map { (palabra, etiqueta, tipo) ->
            async {
                runCatching {
                    val r: RespuestaPhoton = http.get("$BASE/api/") {
                        parameter("q", palabra)
                        parameter("bbox", recuadro)
                        parameter("osm_tag", etiqueta)
                        parameter("limit", 15)
                    }.body()
                    r.aLugares().map { LugarSugerido(it, tipo, distanciaKm(centro, it.coordenada)) }
                }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
    }

    private companion object {
        const val BASE = "https://photon.komoot.io"

        /** (palabra de búsqueda, etiqueta OSM, nombre del tipo para mostrar) */
        val CATEGORIAS = listOf(
            Triple("parque", "leisure:park", "Parque"),
            Triple("plaza", "place:square", "Plaza"),
            Triple("estadio", "leisure:stadium", "Estadio"),
            Triple("mirador", "tourism:viewpoint", "Mirador"),
            Triple("polideportivo", "leisure:sports_centre", "Deportivo"),
            Triple("iglesia", "amenity:place_of_worship", "Iglesia"),
            Triple("universidad", "amenity:university", "Universidad"),
            Triple("santuario", "tourism:attraction", "Atractivo"),
        )
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
