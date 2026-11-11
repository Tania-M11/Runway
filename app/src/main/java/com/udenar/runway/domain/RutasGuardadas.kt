package com.udenar.runway.domain

/** Categorías del catálogo propio de RUNWAY (plan, sección 3.3). */
enum class Visibilidad(val etiqueta: String, val descripcion: String) {
    Privada("Privada", "Solo tú la ves"),
    Reutilizable("Reutilizable", "Plantilla para repetir tus entrenamientos"),
    Compartida("Compartida", "Visible para la comunidad"),
}

/** Una ruta del catálogo personal, guardada en el teléfono. */
data class RutaGuardada(
    val id: String,
    val nombre: String,
    val visibilidad: Visibilidad,
    val modalidad: Modalidad,
    val distanciaKm: Double,
    val subidaM: Double,
    val bajadaM: Double,
    val puntos: List<Coordenada>,
    val elevaciones: List<Double>,
    val intervaloElevacionM: Double,
    val calles: List<String>,
    val giros: Int,
    val inicioNombre: String,
    val finNombre: String?,
    val creadaEn: Long,
    /** Aún no subida a Supabase (parte 8). */
    val pendienteSync: Boolean,
) {
    /** Para reutilizar las pantallas de detalle; tiempo y exigencia se calculan con el nivel actual. */
    fun comoCandidata(nivel: Nivel) = RutaCandidata(
        id = id,
        nombre = nombre,
        puntos = puntos,
        distanciaKm = distanciaKm,
        subidaM = subidaM,
        bajadaM = bajadaM,
        elevaciones = elevaciones,
        intervaloElevacionM = intervaloElevacionM,
        calles = calles,
        giros = giros,
        modalidad = modalidad,
        tiempoMin = Recomendador.tiempoMin(distanciaKm, subidaM, nivel),
        exigencia = Recomendador.exigencia(distanciaKm, subidaM, nivel),
        puntuacion = 0.0,
    )
}

/** Codifica un trazado como "encoded polyline" (el inverso de [decodificarPolyline]). */
fun codificarPolyline(puntos: List<Coordenada>, precision: Int = 6): String {
    val factor = Math.pow(10.0, precision.toDouble())
    val sb = StringBuilder()
    var latAnterior = 0L
    var lonAnterior = 0L
    fun escribir(valor: Long) {
        var v = if (valor < 0) (valor shl 1).inv() else valor shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
    for (p in puntos) {
        val lat = Math.round(p.lat * factor)
        val lon = Math.round(p.lon * factor)
        escribir(lat - latAnterior)
        escribir(lon - lonAnterior)
        latAnterior = lat
        lonAnterior = lon
    }
    return sb.toString()
}
