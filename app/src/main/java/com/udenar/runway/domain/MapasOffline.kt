package com.udenar.runway.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Un área del mapa descargada en el teléfono para verla sin internet. */
data class ZonaOffline(
    val id: Long,
    val nombre: String,
    val suroeste: Coordenada,
    val noreste: Coordenada,
    val creadaEn: Long,
    val completa: Boolean,
    val bytes: Long,
    val recursosCompletados: Long,
    val recursosRequeridos: Long,
) {
    fun cubre(sw: Coordenada, ne: Coordenada) =
        suroeste.lat <= sw.lat && suroeste.lon <= sw.lon && noreste.lat >= ne.lat && noreste.lon >= ne.lon

    fun contiene(c: Coordenada) = c.lat in suroeste.lat..noreste.lat && c.lon in suroeste.lon..noreste.lon
}

/** Progreso de una descarga en curso. */
data class ProgresoDescarga(
    val completados: Long,
    val requeridos: Long,
    val bytes: Long,
    val terminada: Boolean = false,
    val error: String? = null,
) {
    val fraccion: Float get() = if (requeridos <= 0) 0f else (completados.toFloat() / requeridos).coerceIn(0f, 1f)
}

/**
 * Cálculos de teselas para las descargas sin conexión.
 *
 * El mapa está partido en teselas (cuadros de 512 px) por niveles de zoom: en el zoom z hay
 * 2^z × 2^z teselas en el mundo. Al descargar un área se bajan todas las teselas que la tocan
 * entre [ZOOM_MIN] y el zoom máximo que publica el servidor ([ZOOM_MAX_FUENTE]); al acercarse
 * más, el motor reutiliza las del zoom 14 ("overzoom").
 */
object Teselas {
    const val ZOOM_MIN = 11.0
    const val ZOOM_MAX = 16.0
    /** OpenFreeMap publica teselas vectoriales hasta el zoom 14. */
    const val ZOOM_MAX_FUENTE = 14
    /** Límite para no descargar zonas enormes desde el servidor público. */
    const val LIMITE = 3_000L
    /** Peso medio aproximado de una tesela urbana (KB), para estimar. */
    private const val KB_POR_TESELA = 30.0

    private fun x(lon: Double, z: Int) = floor((lon + 180) / 360 * 2.0.pow(z)).toLong()

    private fun y(lat: Double, z: Int): Long {
        val r = Math.toRadians(lat)
        return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * 2.0.pow(z)).toLong()
    }

    /** Cuántas teselas cubren el recuadro entre los zooms indicados. */
    fun contar(sw: Coordenada, ne: Coordenada, zMin: Int = ZOOM_MIN.toInt(), zMax: Int = ZOOM_MAX_FUENTE): Long =
        (zMin..zMax).sumOf { z ->
            (x(ne.lon, z) - x(sw.lon, z) + 1) * (y(sw.lat, z) - y(ne.lat, z) + 1)
        }

    fun megasEstimados(teselas: Long): Double = teselas * KB_POR_TESELA / 1024

    /** Recuadro que encierra los puntos, ampliado [margenKm] hacia cada lado. */
    fun limites(puntos: List<Coordenada>, margenKm: Double = 0.3): Pair<Coordenada, Coordenada> {
        require(puntos.isNotEmpty())
        val sw = Coordenada(puntos.minOf { it.lat }, puntos.minOf { it.lon })
        val ne = Coordenada(puntos.maxOf { it.lat }, puntos.maxOf { it.lon })
        return destino(destino(sw, 180.0, margenKm), 270.0, margenKm) to destino(destino(ne, 0.0, margenKm), 90.0, margenKm)
    }
}
