package com.udenar.runway.ui.inicio

import com.udenar.runway.data.local.RepositorioEntrenamientos
import com.udenar.runway.domain.Entrenamiento
import com.udenar.runway.domain.Estadisticas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Resumen personal de Inicio (especificación 5.2): acumulado, último entrenamiento y pendientes. */
data class DatosInicio(
    val distanciaKm: Double = 0.0,
    val salidas: Int = 0,
    val duracionMs: Long = 0,
    val ultimo: Entrenamiento? = null,
    val pendientes: Int = 0,
)

/** Se actualiza solo cuando cambia la base de datos (al terminar o sincronizar entrenamientos). */
fun datosInicio(repositorio: RepositorioEntrenamientos): Flow<DatosInicio> = repositorio.finalizados().map { lista ->
    val total = Estadisticas.resumen(lista)
    DatosInicio(
        distanciaKm = total.distanciaKm,
        salidas = total.salidas,
        duracionMs = total.duracionMs,
        ultimo = lista.maxByOrNull { it.inicioEn },
        pendientes = lista.count { it.pendienteSync },
    )
}
