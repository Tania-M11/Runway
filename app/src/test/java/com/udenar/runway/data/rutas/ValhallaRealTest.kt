package com.udenar.runway.data.rutas

import com.udenar.runway.data.red.crearClienteHttp
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Lugar
import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.ParametrosRuta
import com.udenar.runway.domain.PreferenciaDesnivel
import com.udenar.runway.domain.formatoKm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prueba contra el servidor público real de Valhalla. No corre por defecto (depende de
 * internet): se activa con la variable de entorno RUNWAY_RED=1.
 */
class ValhallaRealTest {

    @Test
    fun generaCircuitosCercanosALaDistanciaPedida() = runBlocking {
        assumeTrue(System.getenv("RUNWAY_RED") == "1")
        val planificador = PlanificadorRutas(ClienteValhalla(crearClienteHttp()))
        val p = ParametrosRuta(
            Lugar("Ipiales", "", Coordenada(0.8302, -77.6446)),
            5.0, Modalidad.Circular, Nivel.Intermedio, PreferenciaDesnivel.Plano,
        )
        val resultado = planificador.generar(p)
        assertTrue("Resultado: $resultado", resultado is ResultadoPlan.Rutas)
        (resultado as ResultadoPlan.Rutas).rutas.forEach {
            println("${it.nombre}: ${formatoKm(it.distanciaKm)} km, +${it.subidaM.toInt()} m, ${it.exigencia}, puntos=${it.puntos.size}, calles=${it.calles}, puntaje=${"%.2f".format(it.puntuacion)}")
        }
        val mejor = resultado.rutas.first()
        assertTrue(mejor.distanciaKm in 4.0..6.0)
        assertTrue(mejor.puntos.first().let { kotlin.math.abs(it.lat - 0.8302) < 0.003 })
    }

    @Test
    fun generaRutasHaciaUnDestino() = runBlocking {
        assumeTrue(System.getenv("RUNWAY_RED") == "1")
        val planificador = PlanificadorRutas(ClienteValhalla(crearClienteHttp()))
        val base = ParametrosRuta(
            Lugar("Ipiales", "", Coordenada(0.8302, -77.6446)), 5.0, Modalidad.Lineal, Nivel.Intermedio, PreferenciaDesnivel.Plano,
            destino = Lugar("Plaza La Pola", "", Coordenada(0.8256698, -77.6390149)),
        )
        listOf(base, base.copy(modalidad = Modalidad.Circular)).forEach { p ->
            val r = planificador.generar(p)
            assertTrue("Resultado: $r", r is ResultadoPlan.Rutas)
            (r as ResultadoPlan.Rutas).rutas.forEach { println("${p.modalidad}: ${it.nombre} ${formatoKm(it.distanciaKm)} km") }
        }
    }
}
