package com.udenar.runway.data.rutas

import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Destinos
import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.ParametrosRuta
import com.udenar.runway.domain.Recomendador
import com.udenar.runway.domain.RutaCandidata
import com.udenar.runway.domain.decodificarPolyline
import com.udenar.runway.domain.desnivelAcumulado
import com.udenar.runway.domain.destino
import com.udenar.runway.domain.nombreRumbo
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs

sealed interface ResultadoPlan {
    data class Rutas(val rutas: List<RutaCandidata>) : ResultadoPlan
    /** El motor respondió, pero no encontró caminos (p. ej. punto aislado). */
    data object SinRutas : ResultadoPlan
    /** El servidor no respondió o no hay conexión. */
    data object ErrorServicio : ResultadoPlan
}

/**
 * Genera alternativas de recorrido con una distancia objetivo.
 *
 * Valhalla calcula caminos entre puntos, pero no "un circuito de 5 km". Por eso:
 * 1. Se lanzan varias direcciones (norte, este, sur, oeste…).
 * 2. **Circular:** se ponen 2 puntos intermedios formando un triángulo con el inicio
 *    (inicio → A → B → inicio). **Lineal:** un punto final en esa dirección.
 * 3. Se pide la ruta real por calles a Valhalla y, si la distancia se aleja más de un 10 %
 *    del objetivo, se escala el triángulo y se vuelve a pedir (hasta dos correcciones).
 * 4. Cada alternativa se puntúa ([Recomendador]) y se ordenan de mejor a peor.
 */
class PlanificadorRutas(private val valhalla: ClienteValhalla) {

    suspend fun generar(p: ParametrosRuta, alAvanzar: (paso: Int, total: Int) -> Unit = { _, _ -> }): ResultadoPlan {
        p.destino?.let { return generarConDestino(p, it.coordenada, it.nombre, alAvanzar) }
        val candidatas = mutableListOf<RutaCandidata>()
        var fallosDeRed = 0
        var sinCamino = 0

        RUMBOS.forEachIndexed { i, rumbo ->
            alAvanzar(i + 1, RUMBOS.size)
            var escala = 1.0
            var mejor: RespuestaValhalla? = null
            for (intento in 0 until MAX_INTENTOS) {
                val respuesta = try {
                    consultar(puntosGuia(p, rumbo, escala), Recomendador.usoCuestas(p.desnivel))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ClientRequestException) {
                    sinCamino++ // 400: Valhalla no encontró camino para esos puntos
                    null
                } catch (e: Exception) {
                    fallosDeRed++
                    null
                } ?: break
                val largo = respuesta.trip.summary.length
                if (mejor == null || abs(largo - p.distanciaKm) < abs(mejor.trip.summary.length - p.distanciaKm)) {
                    mejor = respuesta
                }
                if (abs(largo - p.distanciaKm) / p.distanciaKm <= 0.10 || largo <= 0) break
                escala *= p.distanciaKm / largo
            }
            mejor?.let { candidatas += construir(it, p, rumbo, id = "ruta-$i") }
        }

        val unicas = candidatas.distinctBy { (it.distanciaKm * 10).toInt() to it.calles.take(2) }
        return when {
            unicas.isNotEmpty() -> ResultadoPlan.Rutas(unicas.sortedByDescending { it.puntuacion })
            fallosDeRed > 0 && sinCamino == 0 -> ResultadoPlan.ErrorServicio
            fallosDeRed > sinCamino -> ResultadoPlan.ErrorServicio
            else -> ResultadoPlan.SinRutas
        }
    }

    /**
     * Rutas hacia un destino elegido por el corredor.
     * - **Lineal:** la ruta directa más las alternativas que propone Valhalla.
     * - **Circular:** ida y vuelta pasando por el destino, más dos variantes que regresan
     *   por otro lado (con un punto de desvío a la izquierda y a la derecha).
     */
    private suspend fun generarConDestino(
        p: ParametrosRuta,
        destino: Coordenada,
        nombreDestino: String,
        alAvanzar: (Int, Int) -> Unit,
    ): ResultadoPlan {
        val inicio = p.inicio.coordenada
        val uso = Recomendador.usoCuestas(p.desnivel)
        val candidatas = mutableListOf<RutaCandidata>()
        var fallosDeRed = 0
        var sinCamino = 0
        val intentos: List<Pair<String, List<Coordenada>>> = when (p.modalidad) {
            Modalidad.Lineal -> listOf("Hacia $nombreDestino" to listOf(inicio, destino))
            Modalidad.Circular -> listOf(
                "Ida y vuelta a $nombreDestino" to listOf(inicio, destino, inicio),
                "Vuelta por la derecha" to listOf(inicio, destino, Destinos.puntoDesvio(inicio, destino, 1), inicio),
                "Vuelta por la izquierda" to listOf(inicio, destino, Destinos.puntoDesvio(inicio, destino, -1), inicio),
            )
        }
        val rumbo = Destinos.rumbo(inicio, destino)
        intentos.forEachIndexed { i, (nombre, puntos) ->
            alAvanzar(i + 1, intentos.size)
            val respuesta = try {
                consultar(puntos, uso, alternativas = if (p.modalidad == Modalidad.Lineal) 2 else 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ClientRequestException) {
                sinCamino++
                null
            } catch (e: Exception) {
                fallosDeRed++
                null
            } ?: return@forEachIndexed
            candidatas += construir(respuesta, p, rumbo, id = "destino-$i").copy(nombre = nombre)
            respuesta.alternates.forEachIndexed { j, alt ->
                candidatas += construir(RespuestaValhalla(alt.trip), p, rumbo, id = "destino-$i-alt$j")
                    .copy(nombre = "Alternativa ${j + 1} hacia $nombreDestino")
            }
        }
        val unicas = candidatas.distinctBy { (it.distanciaKm * 20).toInt() to it.calles.take(2) }
        return when {
            unicas.isNotEmpty() -> ResultadoPlan.Rutas(unicas.sortedByDescending { it.puntuacion })
            fallosDeRed > sinCamino -> ResultadoPlan.ErrorServicio
            else -> ResultadoPlan.SinRutas
        }
    }

    /** Una consulta con un reintento si el servidor público pide esperar (429). */
    private suspend fun consultar(puntos: List<Coordenada>, usoCuestas: Double, alternativas: Int = 0): RespuestaValhalla {
        delay(PAUSA_MS) // ser amables con el servidor público de demostración
        return try {
            valhalla.ruta(puntos, usoCuestas, alternativas)
        } catch (e: ClientRequestException) {
            if (e.response.status != HttpStatusCode.TooManyRequests) throw e
            delay(1_500)
            valhalla.ruta(puntos, usoCuestas, alternativas)
        }
    }

    companion object {
        /** Direcciones de las alternativas (grados desde el norte). */
        val RUMBOS = listOf(15.0, 105.0, 195.0, 285.0)
        /** Las calles no son líneas rectas: el recorrido real es ~30 % más largo. */
        const val FACTOR_RODEO = 1.3
        private const val PAUSA_MS = 250L
        private const val MAX_INTENTOS = 3

        fun puntosGuia(p: ParametrosRuta, rumbo: Double, escala: Double): List<Coordenada> {
            val inicio = p.inicio.coordenada
            return when (p.modalidad) {
                Modalidad.Circular -> {
                    // Triángulo equilátero: 3 lados iguales → perímetro = 3r
                    val r = p.distanciaKm / (3 * FACTOR_RODEO) * escala
                    listOf(inicio, destino(inicio, rumbo - 30, r), destino(inicio, rumbo + 30, r), inicio)
                }
                Modalidad.Lineal -> listOf(inicio, destino(inicio, rumbo, p.distanciaKm / FACTOR_RODEO * escala))
            }
        }

        fun construir(r: RespuestaValhalla, p: ParametrosRuta, rumbo: Double, id: String): RutaCandidata {
            val tramos = r.trip.legs
            val puntos = tramos.flatMap { decodificarPolyline(it.shape) }
            val elevaciones = tramos.flatMap { it.elevation }
            val (subida, bajada) = desnivelAcumulado(elevaciones)
            val distancia = r.trip.summary.length
            val maniobras = tramos.flatMap { it.maneuvers }
            val calles = maniobras
                .filter { it.calles.isNotEmpty() }
                .groupBy { it.calles.first() }
                .mapValues { (_, m) -> m.sumOf { it.length } }
                .entries.sortedByDescending { it.value }
                .take(3).map { it.key }
            val giros = (maniobras.size - 2).coerceAtLeast(0)
            val hacia = nombreRumbo(rumbo)
            return RutaCandidata(
                id = id,
                nombre = if (p.modalidad == Modalidad.Circular) "Circuito hacia el $hacia" else "Ruta hacia el $hacia",
                puntos = puntos,
                distanciaKm = distancia,
                subidaM = subida,
                bajadaM = bajada,
                elevaciones = elevaciones,
                intervaloElevacionM = tramos.firstOrNull()?.intervaloElevacion ?: ClienteValhalla.INTERVALO_ELEVACION_M,
                calles = calles,
                giros = giros,
                modalidad = p.modalidad,
                tiempoMin = Recomendador.tiempoMin(distancia, subida, p.nivel),
                exigencia = Recomendador.exigencia(distancia, subida, p.nivel),
                puntuacion = Recomendador.puntuar(distancia, p.distanciaKm, subida, giros, p.desnivel),
            )
        }
    }
}
