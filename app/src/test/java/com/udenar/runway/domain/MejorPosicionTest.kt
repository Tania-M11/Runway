package com.udenar.runway.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MejorPosicionTest {

    private fun pos(precision: Float, tiempo: Long, gps: Boolean) =
        PosicionGps(Coordenada(0.83, -77.64), precision, tiempo, null, null, gps)

    @Test
    fun laPrimeraLecturaSiempreSeAcepta() = assertTrue(mejorPosicion(null, pos(500f, 0, gps = false)))

    @Test
    fun unaLecturaDeGpsSiempreSeAcepta() = assertTrue(mejorPosicion(pos(5f, 0, true), pos(20f, 1_000, gps = true)))

    @Test
    fun laRedImprecisaNoPisaUnGpsReciente() = assertFalse(mejorPosicion(pos(5f, 0, true), pos(300f, 2_000, gps = false)))

    @Test
    fun laRedSeAceptaSiElGpsQuedoViejo() = assertTrue(mejorPosicion(pos(5f, 0, true), pos(300f, 20_000, gps = false)))

    @Test
    fun laRedMasPrecisaSeAcepta() = assertTrue(mejorPosicion(pos(400f, 0, false), pos(40f, 1_000, gps = false)))
}
