package com.udenar.runway.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeselasTest {

    @Test
    fun enZoomCeroTodoElMundoEsUnaTesela() {
        assertEquals(1L, Teselas.contar(Coordenada(-80.0, -170.0), Coordenada(80.0, 170.0), 0, 0))
    }

    @Test
    fun cadaZoomCuadruplicaLasTeselasDeUnAreaGrande() {
        // ~10 km x 10 km: al subir un zoom hay cerca de 4 veces más teselas (menos por los bordes)
        val sw = Coordenada(0.78, -77.69)
        val ne = Coordenada(0.87, -77.60)
        assertTrue(Teselas.contar(sw, ne, 15, 15) > Teselas.contar(sw, ne, 14, 14) * 2.5)
    }

    @Test
    fun unBarrioCabeHolgadoEnElLimite() {
        // ~2 km x 2 km en Ipiales, zooms 11 a 14
        val (sw, ne) = Teselas.limites(listOf(Coordenada(0.8302, -77.6446)), margenKm = 1.0)
        val n = Teselas.contar(sw, ne)
        assertTrue("teselas = $n", n in 4..Teselas.LIMITE)
    }

    @Test
    fun limitesConMargenYCobertura() {
        val puntos = listOf(Coordenada(0.82, -77.65), Coordenada(0.84, -77.63))
        val (sw, ne) = Teselas.limites(puntos, margenKm = 0.5)
        assertTrue(sw.lat < 0.82 && sw.lon < -77.65 && ne.lat > 0.84 && ne.lon > -77.63)
        val zona = ZonaOffline(1, "z", sw, ne, 0, true, 0, 0, 0)
        assertTrue(zona.cubre(Coordenada(0.82, -77.65), Coordenada(0.84, -77.63)))
        assertTrue(zona.contiene(Coordenada(0.83, -77.64)))
        assertFalse(zona.contiene(Coordenada(0.90, -77.64)))
    }
}
