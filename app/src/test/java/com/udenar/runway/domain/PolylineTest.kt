package com.udenar.runway.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {

    @Test
    fun codificaComoElEjemploDeGoogle() {
        val puntos = listOf(Coordenada(38.5, -120.2), Coordenada(40.7, -120.95), Coordenada(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", codificarPolyline(puntos, precision = 5))
    }

    @Test
    fun idaYVueltaConservaLosPuntosConPrecision6() {
        val puntos = listOf(Coordenada(0.830215, -77.644612), Coordenada(0.8311, -77.6401), Coordenada(-33.45, 151.2))
        val vuelta = decodificarPolyline(codificarPolyline(puntos))
        assertEquals(puntos.size, vuelta.size)
        puntos.zip(vuelta).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-6)
            assertEquals(a.lon, b.lon, 1e-6)
        }
    }
}
