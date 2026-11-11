package com.udenar.runway.data.local

import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Entrenamiento
import com.udenar.runway.domain.decodificarPolyline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Lectura de entrenamientos terminados (resumen e historial). */
class RepositorioEntrenamientos(private val dao: EntrenamientosDao) {

    suspend fun obtener(id: String): Entrenamiento? {
        val e = dao.obtener(id) ?: return null
        return e.aDominio(dao.puntos(id))
    }

    /** Lista con el trazado comprimido (para siluetas); los puntos completos se cargan en el detalle. */
    fun finalizados(): Flow<List<Entrenamiento>> = dao.observarFinalizados().map { lista -> lista.map { it.aDominio(emptyList()) } }
}

internal fun EntrenamientoEntidad.aDominio(puntos: List<PuntoGpsEntidad>) = Entrenamiento(
    id = id,
    rutaNombre = rutaNombre,
    inicioEn = inicioEn,
    finEn = finEn,
    distanciaM = distanciaM,
    duracionMs = duracionMs,
    subidaM = subidaM,
    // Con puntos: tramos reales. Sin ellos (listas): el trazado comprimido que se guardó al finalizar
    segmentos = if (puntos.isNotEmpty()) {
        puntos.groupBy { it.segmento }.toSortedMap().values.map { tramo -> tramo.map { Coordenada(it.lat, it.lon) } }
    } else {
        listOfNotNull(trazado?.let { decodificarPolyline(it) }?.takeIf { it.isNotEmpty() })
    },
    altitudes = puntos.mapNotNull { it.altitudM },
    pendienteSync = pendienteSync,
)
