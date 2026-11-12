package com.udenar.runway.data.sync

import com.udenar.runway.data.local.EntrenamientoEntidad
import com.udenar.runway.data.local.PuntoGpsEntidad
import com.udenar.runway.data.local.RutaEntidad
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.codificarPolyline
import com.udenar.runway.domain.decodificarPolyline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Fila de `public.rutas` en Supabase. */
@Serializable
data class RutaRemota(
    val id: String,
    val nombre: String,
    val visibilidad: String,
    val modalidad: String,
    @SerialName("distancia_km") val distanciaKm: Double,
    @SerialName("subida_m") val subidaM: Double,
    @SerialName("bajada_m") val bajadaM: Double,
    val trazado: String,
    val elevaciones: String,
    @SerialName("intervalo_elevacion_m") val intervaloElevacionM: Double,
    val calles: String,
    val giros: Int,
    @SerialName("inicio_nombre") val inicioNombre: String,
    @SerialName("fin_nombre") val finNombre: String?,
    @SerialName("creada_en") val creadaEn: Long,
    @SerialName("actualizada_en") val actualizadaEn: Long,
    val eliminada: Boolean,
)

/** Fila de `public.entrenamientos`. Los puntos viajan comprimidos: un polyline por tramo. */
@Serializable
data class EntrenamientoRemoto(
    val id: String,
    @SerialName("ruta_id") val rutaId: String?,
    @SerialName("ruta_nombre") val rutaNombre: String?,
    @SerialName("ruta_trazado") val rutaTrazado: String?,
    @SerialName("inicio_en") val inicioEn: Long,
    @SerialName("fin_en") val finEn: Long?,
    @SerialName("distancia_m") val distanciaM: Double,
    @SerialName("duracion_ms") val duracionMs: Long,
    @SerialName("subida_m") val subidaM: Double,
    val segmentos: String,
    val altitudes: String,
    val eliminado: Boolean = false,
)

fun RutaEntidad.aRemota() = RutaRemota(
    id, nombre, visibilidad, modalidad, distanciaKm, subidaM, bajadaM, trazado, elevaciones,
    intervaloElevacionM, calles, giros, inicioNombre, finNombre, creadaEn, actualizadaEn, eliminada,
)

fun RutaRemota.aEntidad() = RutaEntidad(
    id = id, nombre = nombre, visibilidad = visibilidad, modalidad = modalidad, distanciaKm = distanciaKm,
    subidaM = subidaM, bajadaM = bajadaM, trazado = trazado, elevaciones = elevaciones,
    intervaloElevacionM = intervaloElevacionM, calles = calles, giros = giros, inicioNombre = inicioNombre,
    finNombre = finNombre, creadaEn = creadaEn, actualizadaEn = actualizadaEn,
    pendienteSync = false, eliminada = eliminada,
)

fun EntrenamientoEntidad.aRemoto(puntos: List<PuntoGpsEntidad>) = EntrenamientoRemoto(
    id = id,
    rutaId = rutaId,
    rutaNombre = rutaNombre,
    rutaTrazado = rutaTrazado,
    inicioEn = inicioEn,
    finEn = finEn,
    distanciaM = distanciaM,
    duracionMs = duracionMs,
    subidaM = subidaM,
    segmentos = puntos.groupBy { it.segmento }.toSortedMap().values
        .joinToString("|") { tramo -> codificarPolyline(tramo.map { Coordenada(it.lat, it.lon) }) },
    altitudes = puntos.mapNotNull { it.altitudM }.joinToString(";"),
    eliminado = eliminada,
)

/**
 * Reconstruye un entrenamiento descargado. Los puntos recuperan su tramo y su orden;
 * la hora exacta de cada punto no se guarda en el servidor (no hace falta para el historial).
 */
fun EntrenamientoRemoto.aEntidad(): Pair<EntrenamientoEntidad, List<PuntoGpsEntidad>> {
    val tramos = segmentos.split("|").filter { it.isNotBlank() }.map { decodificarPolyline(it) }
    val todos = tramos.flatten()
    val alturas = altitudes.split(";").mapNotNull { it.toDoubleOrNull() }
    val entidad = EntrenamientoEntidad(
        id = id, rutaId = rutaId, rutaNombre = rutaNombre, rutaTrazado = rutaTrazado,
        inicioEn = inicioEn, finEn = finEn, estado = "FINALIZADO", distanciaM = distanciaM,
        duracionMs = duracionMs, tramoInicioEn = null, segmento = (tramos.size - 1).coerceAtLeast(0),
        subidaM = subidaM, trazado = codificarPolyline(todos), actualizadoEn = finEn ?: inicioEn,
        pendienteSync = false, eliminada = eliminado,
    )
    var indice = 0
    val puntos = tramos.flatMapIndexed { numeroTramo, tramo ->
        tramo.map { c ->
            PuntoGpsEntidad(
                entrenamientoId = id,
                segmento = numeroTramo,
                lat = c.lat,
                lon = c.lon,
                // Las altitudes solo se conocen si cada punto tenía una (caso normal del GPS)
                altitudM = if (alturas.size == todos.size) alturas[indice] else null,
                precisionM = 0f,
                velocidadMs = null,
                tiempoMs = inicioEn + indice++,
            )
        }
    }
    return entidad to puntos
}
