package com.udenar.runway.data.sync

import com.udenar.runway.data.local.EntrenamientoEntidad
import com.udenar.runway.data.local.PuntoGpsEntidad
import com.udenar.runway.data.local.RutaEntidad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MapeoTest {

    private fun punto(seg: Int, i: Int) = PuntoGpsEntidad(
        entrenamientoId = "e1", segmento = seg, lat = 0.83 + i * 0.0001, lon = -77.64 - i * 0.0001,
        altitudM = 2900.0 + i, precisionM = 5f, velocidadMs = 3f, tiempoMs = 1_000L * i,
    )

    private val entrenamiento = EntrenamientoEntidad(
        id = "e1", rutaId = null, rutaNombre = "Circuito", rutaTrazado = null, inicioEn = 1_000, finEn = 61_000,
        estado = "FINALIZADO", distanciaM = 1234.5, duracionMs = 60_000, tramoInicioEn = null, segmento = 1,
        subidaM = 12.0, trazado = null, actualizadoEn = 61_000,
    )

    @Test
    fun entrenamientoConDosTramosVaYVuelve() {
        val puntos = (0..4).map { punto(0, it) } + (5..9).map { punto(1, it) }
        val (vuelta, puntosVuelta) = entrenamiento.aRemoto(puntos).aEntidad()

        assertEquals(1234.5, vuelta.distanciaM, 1e-9)
        assertEquals(60_000L, vuelta.duracionMs)
        assertEquals("FINALIZADO", vuelta.estado)
        assertFalse(vuelta.pendienteSync) // lo descargado ya está sincronizado
        assertEquals(10, puntosVuelta.size)
        assertEquals(listOf(0, 1), puntosVuelta.map { it.segmento }.distinct())
        puntos.zip(puntosVuelta).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
            assertEquals(a.altitudM!!, b.altitudM!!, 1e-9)
        }
    }

    @Test
    fun rutaVaYVuelveSinPerderNada() {
        val r = RutaEntidad(
            id = "r1", nombre = "Martes 5K", visibilidad = "Reutilizable", modalidad = "Circular",
            distanciaKm = 5.1, subidaM = 40.0, bajadaM = 39.0, trazado = "_p~iF~ps|U", elevaciones = "1.0;2.0",
            intervaloElevacionM = 30.0, calles = "Carrera 6|Calle 17", giros = 8, inicioNombre = "Mi ubicación",
            finNombre = null, creadaEn = 1, actualizadaEn = 2, pendienteSync = true,
        )
        assertEquals(r.copy(pendienteSync = false), r.aRemota().aEntidad())
    }
}
