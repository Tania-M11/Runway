package com.udenar.runway.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrenamientoTest {

    private val base = Coordenada(0.8302, -77.6446)

    private fun lectura(metrosAlNorte: Double, segundos: Long, precision: Float = 5f) = PosicionGps(
        coordenada = destino(base, 0.0, metrosAlNorte / 1000),
        precisionM = precision,
        tiempoMs = segundos * 1000,
        altitudM = null,
        velocidadMs = null,
        deGps = true,
    )

    @Test
    fun laPrimeraLecturaPrecisaSeAceptaSinDistancia() {
        assertEquals(FiltroGps.Decision.Aceptar(0.0), FiltroGps.evaluar(null, lectura(0.0, 0)))
    }

    @Test
    fun unaLecturaImprecisaNoSeRegistra() {
        assertEquals(FiltroGps.Decision.Imprecisa, FiltroGps.evaluar(null, lectura(0.0, 0, precision = 60f)))
    }

    @Test
    fun correrAUnRitmoNormalSeAcepta() {
        // 3 m/s (5:33 min/km)
        val d = FiltroGps.evaluar(lectura(0.0, 0), lectura(9.0, 3))
        assertTrue(d is FiltroGps.Decision.Aceptar)
        assertEquals(9.0, (d as FiltroGps.Decision.Aceptar).metros, 0.05)
    }

    @Test
    fun unSaltoImposibleSeDescarta() {
        // 200 m en 2 s = 100 m/s
        assertEquals(FiltroGps.Decision.Salto, FiltroGps.evaluar(lectura(0.0, 0), lectura(200.0, 2)))
    }

    @Test
    fun quietoNoSumaDistancia() {
        assertEquals(FiltroGps.Decision.MuyCerca, FiltroGps.evaluar(lectura(0.0, 0), lectura(1.5, 2)))
    }

    @Test
    fun ritmoYFormatos() {
        // 5 km en 25 min → 5:00 /km
        assertEquals("5:00", formatoRitmo(ritmoSegKm(25 * 60_000L, 5_000.0)))
        assertNull(ritmoSegKm(10_000, 20.0))
        assertEquals("--:--", formatoRitmo(null))
        assertEquals("14:05", formatoCronometro((14 * 60 + 5) * 1000L))
        assertEquals("1:02:09", formatoCronometro((3600 + 2 * 60 + 9) * 1000L))
    }
}
