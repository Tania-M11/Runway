package com.udenar.runway.domain

import kotlin.math.cos
import kotlin.math.hypot

/** Dónde está el corredor respecto a la ruta que sigue. */
data class ProyeccionRuta(
    /** Segmento de la ruta más cercano (índice del punto inicial del segmento). */
    val indice: Int,
    /** Punto de la ruta más cercano al corredor. */
    val punto: Coordenada,
    /** Kilómetros de la ruta ya recorridos hasta ese punto. */
    val recorridoKm: Double,
    /** Distancia del corredor a la ruta, en metros. */
    val desvioM: Double,
)

/** Estado de la guía en vivo durante un entrenamiento con ruta de referencia. */
data class EstadoGuia(
    /** Punto de la ruta más cercano ahora (sirve para la línea de regreso y el desvío). */
    val proyeccion: ProyeccionRuta,
    /** Avance real sobre la ruta: solo crece mientras el corredor está en ella. */
    val avance: ProyeccionRuta,
    val totalKm: Double,
    val fueraDeRuta: Boolean,
    val llegada: Boolean,
) {
    val restanteKm: Double get() = (totalKm - avance.recorridoKm).coerceAtLeast(0.0)
    val fraccion: Float get() = if (totalKm <= 0) 0f else (avance.recorridoKm / totalKm).toFloat().coerceIn(0f, 1f)
}

/**
 * Cálculos de la guía en vivo: proyectar la posición del GPS sobre la ruta (punto más cercano
 * de cada segmento) para saber el avance, lo que falta y si el corredor se desvió.
 */
object Guia {
    /** Más de 30 m fuera de la ruta: aviso de desvío. */
    const val UMBRAL_DESVIO_M = 30.0
    /** Menos de 20 m: de vuelta en la ruta (margen distinto para que el aviso no parpadee). */
    const val UMBRAL_REGRESO_M = 20.0
    /** A menos de 40 m del final se considera que llegó. */
    const val UMBRAL_LLEGADA_M = 40.0

    /** Kilómetros acumulados desde el inicio hasta cada punto de la ruta. */
    fun acumuladas(ruta: List<Coordenada>): DoubleArray {
        val acc = DoubleArray(ruta.size)
        for (i in 1 until ruta.size) acc[i] = acc[i - 1] + distanciaKm(ruta[i - 1], ruta[i])
        return acc
    }

    /**
     * Punto de la ruta más cercano a [p], buscando solo entre los segmentos [desde]..[hasta].
     * Se usa un plano local en metros (a escala de una ruta urbana el error es despreciable).
     */
    fun proyectar(
        ruta: List<Coordenada>,
        acumuladas: DoubleArray,
        p: Coordenada,
        desde: Int = 0,
        hasta: Int = ruta.lastIndex - 1,
    ): ProyeccionRuta {
        require(ruta.size >= 2)
        val kx = 111_320.0 * cos(Math.toRadians(p.lat))
        val ky = 110_540.0
        fun x(c: Coordenada) = (c.lon - p.lon) * kx
        fun y(c: Coordenada) = (c.lat - p.lat) * ky
        var mejor: ProyeccionRuta? = null
        for (i in desde.coerceAtLeast(0)..hasta.coerceAtMost(ruta.lastIndex - 1)) {
            val a = ruta[i]
            val b = ruta[i + 1]
            val ax = x(a)
            val ay = y(a)
            val dx = x(b) - ax
            val dy = y(b) - ay
            val largo2 = dx * dx + dy * dy
            val t = if (largo2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / largo2
            val tc = t.coerceIn(0.0, 1.0)
            val qx = ax + tc * dx
            val qy = ay + tc * dy
            val d = hypot(qx, qy)
            if (mejor == null || d < mejor.desvioM) {
                val punto = Coordenada(a.lat + (b.lat - a.lat) * tc, a.lon + (b.lon - a.lon) * tc)
                mejor = ProyeccionRuta(i, punto, acumuladas[i] + (acumuladas[i + 1] - acumuladas[i]) * tc, d)
            }
        }
        return mejor ?: ProyeccionRuta(0, ruta.first(), 0.0, distanciaKm(p, ruta.first()) * 1000)
    }

    /**
     * Proyección que avanza con el corredor: busca cerca del último segmento conocido (así, en
     * un circuito, el inicio y el final que se tocan no se confunden). Si queda lejos, busca en
     * toda la ruta (p. ej. tomó un atajo).
     */
    fun seguir(ruta: List<Coordenada>, acumuladas: DoubleArray, p: Coordenada, ultimoIndice: Int?): ProyeccionRuta {
        if (ultimoIndice == null) return proyectar(ruta, acumuladas, p, 0, minOf(ruta.lastIndex - 1, 40))
        val local = proyectar(ruta, acumuladas, p, ultimoIndice - 3, ultimoIndice + 60)
        return if (local.desvioM <= 60) local else proyectar(ruta, acumuladas, p)
    }

    /**
     * Actualiza el estado de la guía con histéresis: avisa al salir (>30 m) y al volver (<20 m).
     * El avance solo cuenta estando en la ruta: lejos de ella se conserva el último (o el inicio),
     * para no marcar "40 %" a alguien que aún no ha llegado a la ruta.
     */
    fun actualizar(anterior: EstadoGuia?, proyeccion: ProyeccionRuta, ruta: List<Coordenada>): EstadoGuia {
        val totalKm = acumuladas(ruta).last()
        val fuera = if (anterior?.fueraDeRuta == true) proyeccion.desvioM > UMBRAL_REGRESO_M
        else proyeccion.desvioM > UMBRAL_DESVIO_M
        val avance = when {
            !fuera -> proyeccion
            anterior != null -> anterior.avance
            else -> ProyeccionRuta(0, ruta.first(), 0.0, proyeccion.desvioM)
        }
        val llegada = anterior?.llegada == true ||
            !fuera && (totalKm - avance.recorridoKm) * 1000 < UMBRAL_LLEGADA_M && avance.recorridoKm > totalKm * 0.5
        return EstadoGuia(proyeccion, avance, totalKm, fuera, llegada)
    }

    /** Parte la ruta en el tramo ya recorrido y el pendiente (para pintarlos distinto). */
    fun partir(ruta: List<Coordenada>, p: ProyeccionRuta): Pair<List<Coordenada>, List<Coordenada>> {
        val hecho = ruta.subList(0, (p.indice + 1).coerceAtMost(ruta.size)) + p.punto
        val pendiente = listOf(p.punto) + ruta.subList((p.indice + 1).coerceAtMost(ruta.size), ruta.size)
        return hecho to pendiente
    }
}
