package com.udenar.runway.domain

import com.udenar.runway.ui.components.escalaRedonda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EstadisticasTest {

    private val zona = ZoneId.of("America/Bogota")
    // Miércoles 11 de noviembre de 2026, 18:00
    private val ahora = LocalDateTime.of(2026, 11, 11, 18, 0).atZone(zona).toInstant().toEpochMilli()

    private fun hace(dias: Long, km: Double, minutos: Long, id: String = "e$dias-$km") = Entrenamiento(
        id = id,
        rutaNombre = null,
        inicioEn = LocalDateTime.of(2026, 11, 11, 7, 0).minusDays(dias).atZone(zona).toInstant().toEpochMilli(),
        finEn = null,
        distanciaM = km * 1000,
        duracionMs = minutos * 60_000,
        subidaM = 0.0,
        segmentos = emptyList(),
        altitudes = emptyList(),
        pendienteSync = true,
    )

    private val lista = listOf(hace(0, 5.0, 30), hace(2, 3.0, 20), hace(10, 10.0, 55), hace(40, 21.0, 130), hace(400, 8.0, 50))

    @Test
    fun semanaTieneSieteDiasTerminandoHoy() {
        val barras = Estadisticas.barras(lista, PeriodoHistorial.Semana, ahora, zona)
        assertEquals(7, barras.size)
        assertEquals("X", barras.last().etiqueta) // hoy es miércoles
        assertEquals(5.0, barras.last().km, 1e-9)
        assertEquals(3.0, barras[4].km, 1e-9) // lunes
        assertEquals(8.0, barras.sumOf { it.km }, 1e-9)
    }

    @Test
    fun anioAgrupaPorMesYDejaFueraLoMasViejo() {
        val barras = Estadisticas.barras(lista, PeriodoHistorial.Anio, ahora, zona)
        assertEquals(12, barras.size)
        assertEquals("N", barras.last().etiqueta)
        assertEquals(18.0, barras.last().km, 1e-9) // 5 + 3 + 10 en noviembre
        assertEquals(21.0, barras[10].km, 1e-9) // octubre
        assertEquals(39.0, barras.sumOf { it.km }, 1e-9) // el de hace 400 días no entra
    }

    @Test
    fun resumenDelPeriodo() {
        val mes = Estadisticas.enPeriodo(lista, PeriodoHistorial.Mes, ahora, zona)
        val r = Estadisticas.resumen(mes)
        assertEquals(3, r.salidas)
        assertEquals(18.0, r.distanciaKm, 1e-9)
        assertEquals(105L * 60_000, r.duracionMs)
    }

    @Test
    fun recordsUsanTodoElHistorial() {
        val r = Estadisticas.records(lista + hace(1, 0.4, 1, id = "corto"))
        assertEquals(21.0, r.masLarga!!.distanciaM / 1000, 1e-9)
        assertEquals(130L * 60_000, r.masLargaEnTiempo!!.duracionMs)
        // El de 400 m a 2:30 /km no cuenta como mejor ritmo (< 1 km); gana 10 km en 55 min (5:30)
        assertEquals(330.0, r.mejorRitmo!!.ritmoPromedioSegKm!!, 1e-6)
        assertNull(Estadisticas.records(emptyList()).masLarga)
    }

    @Test
    fun escalaRedondaDaNumerosLimpios() {
        assertEquals(10.0, escalaRedonda(7.3), 1e-9)
        assertEquals(2.0, escalaRedonda(1.4), 1e-9)
        assertEquals(50.0, escalaRedonda(42.0), 1e-9)
        assertEquals(0.0, escalaRedonda(0.0), 1e-9)
    }
}
