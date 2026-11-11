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

/** Lectura del GPS del teléfono (mecanismo 1: hardware de geolocalización). */
data class PosicionGps(
    val coordenada: Coordenada,
    val precisionM: Float,
    val tiempoMs: Long,
    val altitudM: Double?,
    val velocidadMs: Float?,
    val deGps: Boolean,
)

/**
 * Decide si una lectura nueva reemplaza a la anterior. Se combinan el GPS (preciso) y la
 * red (rápida pero imprecisa): una lectura de red no debe pisar a una de GPS reciente y mejor.
 */
fun mejorPosicion(anterior: PosicionGps?, nueva: PosicionGps): Boolean = when {
    anterior == null -> true
    nueva.deGps -> true
    nueva.tiempoMs - anterior.tiempoMs > 15_000 -> true
    else -> nueva.precisionM <= anterior.precisionM
}

enum class Nivel(val etiqueta: String, val descripcion: String) {
    Principiante("Principiante", "Estoy empezando o corro menos de 3 km"),
    Intermedio("Intermedio", "Corro unos 5 km con regularidad"),
    Avanzado("Avanzado", "Corro 10 km o más sin problema"),
}

enum class PreferenciaDesnivel(val etiqueta: String, val descripcion: String) {
    Plano("Plano", "Calles sin subidas fuertes"),
    Moderado("Moderado", "Algunas subidas suaves"),
    Exigente("Exigente", "Busco cuestas para entrenar"),
}

/** Datos mínimos del corredor para personalizar las recomendaciones (especificación 5.1). */
data class PerfilCorredor(
    val nombre: String,
    val nivel: Nivel,
    val desnivel: PreferenciaDesnivel,
)
