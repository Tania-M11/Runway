package com.udenar.runway.data.rutas

import com.udenar.runway.domain.Coordenada
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Cliente del motor de rutas Valhalla (servidor público de demostración de FOSSGIS,
 * con datos de OpenStreetMap). Calcula trayectos peatonales con altimetría.
 */
class ClienteValhalla(private val http: HttpClient) {

    /**
     * Ruta peatonal que pasa por [puntos] en orden. Los intermedios son "through":
     * el recorrido los atraviesa sin dar media vuelta (útil para circuitos).
     */
    suspend fun ruta(puntos: List<Coordenada>, usoCuestas: Double, alternativas: Int = 0): RespuestaValhalla {
        val cuerpo = buildJsonObject {
            putJsonArray("locations") {
                puntos.forEachIndexed { i, p ->
                    addJsonObject {
                        put("lat", p.lat)
                        put("lon", p.lon)
                        put("type", if (i == 0 || i == puntos.lastIndex) "break" else "through")
                    }
                }
            }
            put("costing", "pedestrian")
            putJsonObject("costing_options") {
                putJsonObject("pedestrian") {
                    put("use_hills", usoCuestas)
                    put("walkway_factor", 0.9) // preferir senderos peatonales
                    put("sidewalk_factor", 0.95)
                    put("use_living_streets", 0.6)
                    put("use_ferry", 0.0)
                }
            }
            // Valhalla puede proponer rutas alternativas entre dos puntos (no con intermedios)
            if (alternativas > 0 && puntos.size == 2) put("alternates", alternativas)
            put("units", "kilometers")
            put("elevation_interval", INTERVALO_ELEVACION_M)
            put("directions_type", "maneuvers")
            put("language", "es-ES")
        }
        return http.post(URL) {
            contentType(ContentType.Application.Json)
            setBody(cuerpo)
        }.body()
    }

    companion object {
        const val URL = "https://valhalla1.openstreetmap.de/route"
        const val INTERVALO_ELEVACION_M = 30.0
    }
}

@Serializable
data class RespuestaValhalla(
    val trip: ViajeValhalla,
    val alternates: List<AlternativaValhalla> = emptyList(),
)

@Serializable
data class AlternativaValhalla(val trip: ViajeValhalla)

@Serializable
data class ViajeValhalla(val legs: List<TramoValhalla>, val summary: ResumenValhalla)

@Serializable
data class ResumenValhalla(val length: Double, val time: Double = 0.0)

@Serializable
data class TramoValhalla(
    val shape: String,
    val elevation: List<Double> = emptyList(),
    @SerialName("elevation_interval") val intervaloElevacion: Double = ClienteValhalla.INTERVALO_ELEVACION_M,
    val maneuvers: List<ManiobraValhalla> = emptyList(),
)

@Serializable
data class ManiobraValhalla(
    val length: Double = 0.0,
    @SerialName("street_names") val calles: List<String> = emptyList(),
)
