package com.udenar.runway.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class Modalidad(val etiqueta: String, val descripcion: String) {
    Circular("Circular", "Terminas donde empiezas"),
    Lineal("Lineal", "De un punto a otro"),
}

/** Lo que el corredor pide antes de consultar el motor de rutas (especificación 4.1). */
data class ParametrosRuta(
    val inicio: Lugar,
    val distanciaKm: Double,
    val modalidad: Modalidad,
    val nivel: Nivel,
    val desnivel: PreferenciaDesnivel,
    /** Destino opcional: si está, las rutas llegan a él (lineal) o pasan por él (circular). */
    val destino: Lugar? = null,
)

enum class Exigencia(val etiqueta: String) { Suave("Suave"), Moderada("Moderada"), Exigente("Exigente") }

/** Una alternativa de recorrido ya calculada y puntuada. */
data class RutaCandidata(
    val id: String,
    val nombre: String,
    val puntos: List<Coordenada>,
    val distanciaKm: Double,
    val subidaM: Double,
    val bajadaM: Double,
    /** Altitudes a lo largo de la ruta, una cada [intervaloElevacionM] metros. */
    val elevaciones: List<Double>,
    val intervaloElevacionM: Double,
    val calles: List<String>,
    val giros: Int,
    val modalidad: Modalidad,
    val tiempoMin: Int,
    val exigencia: Exigencia,
    val puntuacion: Double,
) {
    val inicio: Coordenada get() = puntos.first()
    val fin: Coordenada get() = puntos.last()
}

// ---------------------------------------------------------------- Geometría

private const val RADIO_TIERRA_KM = 6371.0

/** Punto a [distKm] desde [origen] en la dirección [rumboGrados] (0 = norte, 90 = este). */
fun destino(origen: Coordenada, rumboGrados: Double, distKm: Double): Coordenada {
    val d = distKm / RADIO_TIERRA_KM
    val r = Math.toRadians(rumboGrados)
    val lat1 = Math.toRadians(origen.lat)
    val lon1 = Math.toRadians(origen.lon)
    val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(r))
    val lon2 = lon1 + atan2(sin(r) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
    return Coordenada(Math.toDegrees(lat2), (Math.toDegrees(lon2) + 540) % 360 - 180)
}

/** Distancia en línea recta (fórmula del haversine). */
fun distanciaKm(a: Coordenada, b: Coordenada): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * RADIO_TIERRA_KM * asin(sqrt(h))
}

fun nombreRumbo(grados: Double): String {
    val nombres = listOf("norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste")
    return nombres[(((grados % 360) + 360) % 360 / 45.0).roundToInt() % 8]
}

/** Decodifica un trazado "encoded polyline" (Valhalla usa precisión 6). */
fun decodificarPolyline(texto: String, precision: Int = 6): List<Coordenada> {
    val factor = Math.pow(10.0, precision.toDouble())
    val puntos = ArrayList<Coordenada>()
    var i = 0
    var lat = 0L
    var lon = 0L
    fun siguiente(): Long {
        var resultado = 0L
        var desplazamiento = 0
        var b: Int
        do {
            b = texto[i++].code - 63
            resultado = resultado or ((b and 0x1f).toLong() shl desplazamiento)
            desplazamiento += 5
        } while (b >= 0x20)
        return if (resultado and 1L != 0L) (resultado shr 1).inv() else resultado shr 1
    }
    while (i < texto.length) {
        lat += siguiente()
        lon += siguiente()
        puntos += Coordenada(lat / factor, lon / factor)
    }
    return puntos
}

/**
 * Subida y bajada acumuladas. Solo cuenta cambios mayores a [umbralM] para no sumar
 * el ruido del modelo de elevación (si no, una calle plana "subiría" decenas de metros).
 */
fun desnivelAcumulado(elevaciones: List<Double>, umbralM: Double = 3.0): Pair<Double, Double> {
    if (elevaciones.isEmpty()) return 0.0 to 0.0
    var subida = 0.0
    var bajada = 0.0
    var referencia = elevaciones.first()
    for (e in elevaciones) {
        val cambio = e - referencia
        if (cambio >= umbralM) {
            subida += cambio
            referencia = e
        } else if (cambio <= -umbralM) {
            bajada -= cambio
            referencia = e
        }
    }
    return subida to bajada
}

// ---------------------------------------------------------------- Recomendación

/**
 * Criterios de puntuación de la especificación 4.2: coincidencia de distancia, desnivel
 * adecuado a la preferencia y continuidad de la vía. La popularidad comunitaria no se usa
 * todavía porque no hay datos (la especificación pide no mostrarla sin datos suficientes).
 */
object Recomendador {

    /** Subida por kilómetro (m/km) que se considera ideal para cada preferencia. */
    fun bandaDesnivel(p: PreferenciaDesnivel): ClosedFloatingPointRange<Double> = when (p) {
        PreferenciaDesnivel.Plano -> 0.0..10.0
        PreferenciaDesnivel.Moderado -> 8.0..25.0
        PreferenciaDesnivel.Exigente -> 20.0..80.0
    }

    /** Valor de `use_hills` para el costeo peatonal de Valhalla (0 evita cuestas, 1 las busca). */
    fun usoCuestas(p: PreferenciaDesnivel): Double = when (p) {
        PreferenciaDesnivel.Plano -> 0.0
        PreferenciaDesnivel.Moderado -> 0.5
        PreferenciaDesnivel.Exigente -> 1.0
    }

    fun puntuar(distanciaKm: Double, objetivoKm: Double, subidaM: Double, giros: Int, preferencia: PreferenciaDesnivel): Double {
        val errorDistancia = abs(distanciaKm - objetivoKm) / objetivoKm
        val sDistancia = (1 - errorDistancia * 2.5).coerceIn(0.0, 1.0)

        val subidaPorKm = subidaM / max(distanciaKm, 0.1)
        val banda = bandaDesnivel(preferencia)
        val sDesnivel = when {
            subidaPorKm in banda -> 1.0
            subidaPorKm < banda.start -> 1 - (banda.start - subidaPorKm) / 15
            else -> 1 - (subidaPorKm - banda.endInclusive) / 20
        }.coerceIn(0.0, 1.0)

        val girosPorKm = giros / max(distanciaKm, 0.1)
        val sContinuidad = (1 - (girosPorKm - 3) / 12).coerceIn(0.0, 1.0)

        return 0.5 * sDistancia + 0.3 * sDesnivel + 0.2 * sContinuidad
    }

    /** Exigencia relativa al nivel del corredor: esfuerzo ≈ km + 1 km por cada 100 m de subida. */
    fun exigencia(distanciaKm: Double, subidaM: Double, nivel: Nivel): Exigencia {
        val esfuerzo = distanciaKm + subidaM / 100
        val (suave, moderada) = when (nivel) {
            Nivel.Principiante -> 4.0 to 7.0
            Nivel.Intermedio -> 7.0 to 12.0
            Nivel.Avanzado -> 12.0 to 20.0
        }
        return when {
            esfuerzo <= suave -> Exigencia.Suave
            esfuerzo <= moderada -> Exigencia.Moderada
            else -> Exigencia.Exigente
        }
    }

    /** Tiempo estimado corriendo: ritmo según nivel + 0,6 min por cada 10 m de subida. */
    fun tiempoMin(distanciaKm: Double, subidaM: Double, nivel: Nivel): Int {
        val ritmo = when (nivel) {
            Nivel.Principiante -> 7.5
            Nivel.Intermedio -> 6.0
            Nivel.Avanzado -> 5.0
        }
        return (distanciaKm * ritmo + subidaM / 10 * 0.6).roundToInt()
    }

    fun distanciaSugerida(nivel: Nivel): Double = when (nivel) {
        Nivel.Principiante -> 3.0
        Nivel.Intermedio -> 5.0
        Nivel.Avanzado -> 10.0
    }
}

// ---------------------------------------------------------------- Formato

private val ES = Locale.forLanguageTag("es-CO")

fun formatoKm(km: Double): String = String.format(ES, "%.1f", km)
fun formatoMetros(m: Double): String = String.format(ES, "%.0f", m)
fun formatoDuracion(min: Int): String = if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
