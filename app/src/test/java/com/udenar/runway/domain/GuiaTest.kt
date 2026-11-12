package com.udenar.runway.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuiaTest {

    private val inicio = Coordenada(0.8302, -77.6446)
    // Ruta recta de 1 km hacia el norte, con un punto cada 100 m
    private val ruta = (0..10).map { destino(inicio, 0.0, it * 0.1) }
    private val acc = Guia.acumuladas(ruta)

    @Test
    fun acumuladasSumanLaRuta() {
        assertEquals(1.0, acc.last(), 0.001)
    }

    @Test
    fun enLaRutaElDesvioEsCeroYElAvanceCorrecto() {
        val p = destino(inicio, 0.0, 0.35)
        val pr = Guia.proyectar(ruta, acc, p)
        assertEquals(0.35, pr.recorridoKm, 0.002)
        assertTrue(pr.desvioM < 1)
        assertEquals(3, pr.indice)
    }

    @Test
    fun aUnLadoMideElDesvio() {
        val p = destino(destino(inicio, 0.0, 0.5), 90.0, 0.045) // 45 m al este de la mitad
        val pr = Guia.proyectar(ruta, acc, p)
        assertEquals(45.0, pr.desvioM, 1.0)
        assertEquals(0.5, pr.recorridoKm, 0.002)
    }

    @Test
    fun avisoDeDesvioConHisteresis() {
        fun pr(desvio: Double) = ProyeccionRuta(5, ruta[5], 0.5, desvio)
        val e1 = Guia.actualizar(null, pr(10.0), 1.0)
        assertFalse(e1.fueraDeRuta)
        val e2 = Guia.actualizar(e1, pr(35.0), 1.0)
        assertTrue(e2.fueraDeRuta) // más de 30 m: fuera
        val e3 = Guia.actualizar(e2, pr(25.0), 1.0)
        assertTrue(e3.fueraDeRuta) // entre 20 y 30 m sigue fuera (no parpadea)
        val e4 = Guia.actualizar(e3, pr(15.0), 1.0)
        assertFalse(e4.fueraDeRuta) // menos de 20 m: de vuelta
        assertEquals(0.5, e4.restanteKm, 1e-9)
    }

    @Test
    fun enUnCircuitoElFinalNoSeConfundeConElInicio() {
        // Ida 500 m al norte y vuelta por el mismo camino (el inicio y el final coinciden)
        val ida = (0..5).map { destino(inicio, 0.0, it * 0.1) }
        val circuito = ida + ida.reversed().drop(1)
        val a = Guia.acumuladas(circuito)
        val cercaDelFinal = destino(inicio, 0.0, 0.05)
        // Ya iba por el segmento 8 (de regreso): debe seguir avanzando, no volver a 0
        val pr = Guia.seguir(circuito, a, cercaDelFinal, ultimoIndice = 8)
        assertTrue("recorrido = ${pr.recorridoKm}", pr.recorridoKm > 0.9)
        val llegada = Guia.actualizar(null, pr.copy(recorridoKm = 0.98), a.last())
        assertTrue(llegada.llegada)
    }

    @Test
    fun partirSeparaHechoYPendiente() {
        val pr = Guia.proyectar(ruta, acc, destino(inicio, 0.0, 0.35))
        val (hecho, pendiente) = Guia.partir(ruta, pr)
        assertEquals(pr.punto, hecho.last())
        assertEquals(pr.punto, pendiente.first())
        assertEquals(ruta.last(), pendiente.last())
    }
}
