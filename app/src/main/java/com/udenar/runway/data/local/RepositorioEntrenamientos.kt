package com.udenar.runway.data.local

import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Entrenamiento
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Lectura de entrenamientos terminados (resumen e historial). */
class RepositorioEntrenamientos(private val dao: EntrenamientosDao) {

    suspend fun obtener(id: String): Entrenamiento? {
        val e = dao.obtener(id) ?: return null
        return e.aDominio(dao.puntos(id))
    }

    /** Lista sin trazado (más liviana); el trazado se carga al abrir el detalle. */
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
    segmentos = puntos.groupBy { it.segmento }.toSortedMap().values.map { tramo -> tramo.map { Coordenada(it.lat, it.lon) } },
    altitudes = puntos.mapNotNull { it.altitudM },
    pendienteSync = pendienteSync,
)
