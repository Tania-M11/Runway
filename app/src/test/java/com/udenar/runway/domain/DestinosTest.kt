package com.udenar.runway.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinosTest {

    private val inicio = Coordenada(0.8302, -77.6446)

    private fun sug(nombre: String, km: Double) = LugarSugerido(Lugar(nombre, "", destino(inicio, 45.0, km)), "Parque", km)

    @Test
    fun radioIdealSegunModalidad() {
        assertEquals(5.0 / 1.3, Destinos.radioIdealKm(5.0, Modalidad.Lineal), 1e-9)
        assertEquals(5.0 / 2.6, Destinos.radioIdealKm(5.0, Modalidad.Circular), 1e-9)
    }

    @Test
    fun filtraPorAnilloQuitaRepetidosYOrdenaPorCercaniaAlRadio() {
        val r = 2.0
        val lista = Destinos.filtrar(
            listOf(sug("Muy cerca", 0.3), sug("Parque A", 1.9), sug("parque a", 1.95), sug("Plaza B", 2.9), sug("Lejos", 5.0)),
            r,
        )
        assertEquals(listOf("Parque A", "Plaza B"), lista.map { it.lugar.nombre })
    }

    @Test
    fun elDesvioQuedaALosLadosDelCamino() {
        val destino = destino(inicio, 0.0, 2.0) // 2 km al norte
        val derecha = Destinos.puntoDesvio(inicio, destino, 1)
        val izquierda = Destinos.puntoDesvio(inicio, destino, -1)
        assertTrue(derecha.lon > inicio.lon) // al este
        assertTrue(izquierda.lon < inicio.lon) // al oeste
        assertEquals(0.7, distanciaKm(Coordenada((inicio.lat + destino.lat) / 2, (inicio.lon + destino.lon) / 2), derecha), 0.01)
    }

    @Test
    fun rumboHaciaElNorteYElEste() {
        assertEquals(0.0, Destinos.rumbo(inicio, destino(inicio, 0.0, 1.0)), 0.5)
        assertEquals(90.0, Destinos.rumbo(inicio, destino(inicio, 90.0, 1.0)), 0.5)
    }
}
