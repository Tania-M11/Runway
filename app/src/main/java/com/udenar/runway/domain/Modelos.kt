package com.udenar.runway.domain

/** Punto geográfico en grados decimales (WGS84). */
data class Coordenada(val lat: Double, val lon: Double) {
    fun texto(): String = "%.5f, %.5f".format(java.util.Locale.US, lat, lon)
}

/** Lugar con nombre legible (resultado del buscador o punto tocado en el mapa). */
data class Lugar(
    val nombre: String,
    val detalle: String,
    val coordenada: Coordenada,
)

/** Centro y acercamiento de la cámara del mapa. */
data class PosicionCamara(val centro: Coordenada, val zoom: Double)

/**
 * Posición inicial solo cuando no hay ubicación ni una cámara guardada.
 * Ipiales es la zona de las primeras pruebas, pero el usuario puede buscar
 * o moverse a cualquier lugar del mundo.
 */
val CAMARA_POR_DEFECTO = PosicionCamara(Coordenada(0.8302, -77.6446), zoom = 14.0)
