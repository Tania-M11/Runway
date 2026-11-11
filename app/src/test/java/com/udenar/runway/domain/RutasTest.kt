package com.udenar.runway.domain

import com.udenar.runway.data.rutas.PlanificadorRutas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RutasTest {

    private val ipiales = Coordenada(0.8302, -77.6446)

    @Test
    fun decodificaPolylineDeEjemploDeGoogle() {
        val puntos = decodificarPolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)
        assertEquals(3, puntos.size)
        assertEquals(38.5, puntos[0].lat, 1e-5)
        assertEquals(-120.2, puntos[0].lon, 1e-5)
        assertEquals(43.252, puntos[2].lat, 1e-5)
        assertEquals(-126.453, puntos[2].lon, 1e-5)
    }

    @Test
    fun destinoQuedaALaDistanciaPedida() {
        listOf(0.0, 90.0, 215.0).forEach { rumbo ->
            assertEquals(2.0, distanciaKm(ipiales, destino(ipiales, rumbo, 2.0)), 0.001)
        }
        assertTrue(destino(ipiales, 0.0, 1.0).lat > ipiales.lat) // norte
        assertTrue(destino(ipiales, 90.0, 1.0).lon > ipiales.lon) // este
    }

    @Test
    fun nombresDeRumbo() {
        assertEquals("norte", nombreRumbo(15.0))
        assertEquals("este", nombreRumbo(105.0))
        assertEquals("sur", nombreRumbo(195.0))
        assertEquals("oeste", nombreRumbo(285.0))
    }

    @Test
    fun desnivelIgnoraElRuidoMenorAlUmbral() {
        assertEquals(0.0 to 0.0, desnivelAcumulado(listOf(2900.0, 2901.0, 2900.5, 2901.5, 2900.0)))
        assertEquals(30.0 to 20.0, desnivelAcumulado(listOf(2900.0, 2910.0, 2930.0, 2925.0, 2910.0)))
    }

    @Test
    fun circuitoEsUnTrianguloQueVuelveAlInicio() {
        val p = ParametrosRuta(Lugar("Inicio", "", ipiales), 5.0, Modalidad.Circular, Nivel.Intermedio, PreferenciaDesnivel.Plano)
        val guia = PlanificadorRutas.puntosGuia(p, rumbo = 15.0, escala = 1.0)
        assertEquals(4, guia.size)
        assertEquals(guia.first(), guia.last())
        val lado = 5.0 / (3 * PlanificadorRutas.FACTOR_RODEO)
        assertEquals(lado, distanciaKm(ipiales, guia[1]), 0.001)
        assertEquals(lado, distanciaKm(guia[1], guia[2]), 0.01) // equilátero
    }

    @Test
    fun laRutaMasCercanaALaDistanciaYAlDesnivelGana() {
        val buena = Recomendador.puntuar(5.1, 5.0, subidaM = 30.0, giros = 15, preferencia = PreferenciaDesnivel.Plano)
        val larga = Recomendador.puntuar(7.0, 5.0, subidaM = 30.0, giros = 15, preferencia = PreferenciaDesnivel.Plano)
        val empinada = Recomendador.puntuar(5.1, 5.0, subidaM = 250.0, giros = 15, preferencia = PreferenciaDesnivel.Plano)
        assertTrue(buena > larga)
        assertTrue(buena > empinada)
        // Pero si el corredor busca cuestas, la empinada sí le sirve
        assertTrue(
            Recomendador.puntuar(5.1, 5.0, 250.0, 15, PreferenciaDesnivel.Exigente) >
                Recomendador.puntuar(5.1, 5.0, 30.0, 15, PreferenciaDesnivel.Exigente),
        )
    }

    @Test
    fun exigenciaDependeDelNivel() {
        assertEquals(Exigencia.Exigente, Recomendador.exigencia(8.0, 50.0, Nivel.Principiante))
        assertEquals(Exigencia.Suave, Recomendador.exigencia(8.0, 50.0, Nivel.Avanzado))
    }
}
