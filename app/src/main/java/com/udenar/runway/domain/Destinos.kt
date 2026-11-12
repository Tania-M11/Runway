package com.udenar.runway.domain

import kotlin.math.abs

/** Lugar sugerido como destino (parque, plaza, estadio, mirador…). */
data class LugarSugerido(
    val lugar: Lugar,
    val tipo: String,
    /** Distancia en línea recta desde el inicio. */
    val distanciaRectaKm: Double,
)

object Destinos {

    /**
     * Distancia en línea recta a la que conviene buscar destinos para cumplir la distancia
     * objetivo. Las calles no son rectas (×1,3) y en un circuito se va y se vuelve (×2).
     */
    fun radioIdealKm(distanciaKm: Double, modalidad: Modalidad): Double = when (modalidad) {
        Modalidad.Lineal -> distanciaKm / 1.3
        Modalidad.Circular -> distanciaKm / 2.6
    }

    /**
     * Se quedan los lugares entre 40 % y 160 % del radio ideal, sin repetidos, ordenados por
     * cercanía a ese radio (los primeros son los que mejor cumplen la distancia pedida).
     */
    fun filtrar(candidatos: List<LugarSugerido>, radioIdealKm: Double, maximo: Int = 8): List<LugarSugerido> =
        candidatos
            .filter { it.distanciaRectaKm in (radioIdealKm * 0.4)..(radioIdealKm * 1.6) }
            .distinctBy { it.lugar.nombre.lowercase() }
            .sortedBy { abs(it.distanciaRectaKm - radioIdealKm) }
            .take(maximo)

    /**
     * Punto de desvío para la variante de un circuito: a mitad del camino al destino, corrido
     * hacia un lado (perpendicular) para que la vuelta sea por otras calles.
     */
    fun puntoDesvio(inicio: Coordenada, destino: Coordenada, lado: Int): Coordenada {
        val medio = Coordenada((inicio.lat + destino.lat) / 2, (inicio.lon + destino.lon) / 2)
        val rumbo = rumbo(inicio, destino)
        return destino(medio, rumbo + 90.0 * lado, distanciaKm(inicio, destino) * 0.35)
    }

    /** Rumbo inicial de a hacia b en grados (0 = norte). */
    fun rumbo(a: Coordenada, b: Coordenada): Double {
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = kotlin.math.sin(dLon) * kotlin.math.cos(la2)
        val x = kotlin.math.cos(la1) * kotlin.math.sin(la2) - kotlin.math.sin(la1) * kotlin.math.cos(la2) * kotlin.math.cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360) % 360
    }
}
