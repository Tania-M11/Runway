package com.udenar.runway.data.local

import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.RutaCandidata
import com.udenar.runway.domain.RutaGuardada
import com.udenar.runway.domain.Visibilidad
import com.udenar.runway.domain.codificarPolyline
import com.udenar.runway.domain.decodificarPolyline
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Catálogo propio de rutas (Mis rutas). Las pantallas no ven Room, solo modelos de dominio. */
class RepositorioRutas(private val dao: RutasDao, private val reloj: () -> Long = System::currentTimeMillis) {

    fun rutas(): Flow<List<RutaGuardada>> = dao.observarTodas().map { lista -> lista.map { it.aDominio() } }

    fun ruta(id: String): Flow<RutaGuardada?> = dao.observar(id).map { it?.aDominio() }

    /** Guarda una alternativa calculada y devuelve el id de la ruta guardada. */
    suspend fun guardar(
        ruta: RutaCandidata,
        nombre: String,
        visibilidad: Visibilidad,
        inicioNombre: String,
        finNombre: String?,
    ): String {
        val ahora = reloj()
        val id = UUID.randomUUID().toString()
        dao.insertar(
            RutaEntidad(
                id = id,
                nombre = nombre.trim().ifBlank { ruta.nombre },
                visibilidad = visibilidad.name,
                modalidad = ruta.modalidad.name,
                distanciaKm = ruta.distanciaKm,
                subidaM = ruta.subidaM,
                bajadaM = ruta.bajadaM,
                trazado = codificarPolyline(ruta.puntos),
                elevaciones = ruta.elevaciones.joinToString(";"),
                intervaloElevacionM = ruta.intervaloElevacionM,
                calles = ruta.calles.joinToString("|"),
                giros = ruta.giros,
                inicioNombre = inicioNombre,
                finNombre = finNombre,
                creadaEn = ahora,
                actualizadaEn = ahora,
            ),
        )
        return id
    }

    suspend fun renombrar(id: String, nombre: String) {
        if (nombre.isNotBlank()) dao.renombrar(id, nombre.trim(), reloj())
    }

    suspend fun cambiarVisibilidad(id: String, visibilidad: Visibilidad) = dao.cambiarVisibilidad(id, visibilidad.name, reloj())

    suspend fun eliminar(id: String) = dao.marcarEliminada(id, reloj())
}

private fun RutaEntidad.aDominio() = RutaGuardada(
    id = id,
    nombre = nombre,
    visibilidad = runCatching { Visibilidad.valueOf(visibilidad) }.getOrDefault(Visibilidad.Privada),
    modalidad = runCatching { Modalidad.valueOf(modalidad) }.getOrDefault(Modalidad.Circular),
    distanciaKm = distanciaKm,
    subidaM = subidaM,
    bajadaM = bajadaM,
    puntos = decodificarPolyline(trazado),
    elevaciones = elevaciones.split(";").mapNotNull { it.toDoubleOrNull() },
    intervaloElevacionM = intervaloElevacionM,
    calles = calles.split("|").filter { it.isNotBlank() },
    giros = giros,
    inicioNombre = inicioNombre,
    finNombre = finNombre,
    creadaEn = creadaEn,
    pendienteSync = pendienteSync,
)
