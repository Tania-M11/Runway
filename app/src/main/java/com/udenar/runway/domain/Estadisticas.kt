package com.udenar.runway.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Periodos del historial (especificación 5.10). */
enum class PeriodoHistorial(val etiqueta: String, val tituloGrafica: String) {
    Semana("7 días", "por día"),
    Mes("30 días", "por día"),
    Anio("12 meses", "por mes"),
}

/** Una barra de la gráfica: un día o un mes. */
data class Barra(val etiqueta: String, val km: Double, val minutos: Double)

data class ResumenPeriodo(
    val distanciaKm: Double,
    val salidas: Int,
    val duracionMs: Long,
    val subidaM: Double,
) {
    val ritmoSegKm: Double? get() = ritmoSegKm(duracionMs, distanciaKm * 1000)
}

/** Récords personales (plan, sección 2). */
data class Records(
    val masLarga: Entrenamiento?,
    val mejorRitmo: Entrenamiento?,
    val masLargaEnTiempo: Entrenamiento?,
)

/** Cálculos del historial. Funciones puras: reciben la hora actual y la zona para poder probarlas. */
object Estadisticas {

    private val DIAS = listOf("L", "M", "X", "J", "V", "S", "D")
    private val MESES = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

    /** Primer instante del periodo (inclusive). */
    fun inicioPeriodo(periodo: PeriodoHistorial, ahora: Long, zona: ZoneId): Long {
        val hoy = Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate()
        val desde = when (periodo) {
            PeriodoHistorial.Semana -> hoy.minusDays(6)
            PeriodoHistorial.Mes -> hoy.minusDays(29)
            PeriodoHistorial.Anio -> YearMonth.from(hoy).minusMonths(11).atDay(1)
        }
        return desde.atStartOfDay(zona).toInstant().toEpochMilli()
    }

    fun enPeriodo(lista: List<Entrenamiento>, periodo: PeriodoHistorial, ahora: Long, zona: ZoneId): List<Entrenamiento> {
        val desde = inicioPeriodo(periodo, ahora, zona)
        return lista.filter { it.inicioEn in desde..ahora }
    }

    /** Barras del periodo, incluidos los días o meses sin entrenamientos (en cero). */
    fun barras(lista: List<Entrenamiento>, periodo: PeriodoHistorial, ahora: Long, zona: ZoneId): List<Barra> {
        val hoy = Instant.ofEpochMilli(ahora).atZone(zona).toLocalDate()
        fun fecha(e: Entrenamiento): LocalDate = Instant.ofEpochMilli(e.inicioEn).atZone(zona).toLocalDate()
        return when (periodo) {
            PeriodoHistorial.Semana, PeriodoHistorial.Mes -> {
                val dias = if (periodo == PeriodoHistorial.Semana) 7L else 30L
                val porDia = lista.groupBy { fecha(it) }
                (dias - 1 downTo 0).map { atras ->
                    val dia = hoy.minusDays(atras)
                    val del = porDia[dia].orEmpty()
                    Barra(
                        etiqueta = if (periodo == PeriodoHistorial.Semana) DIAS[dia.dayOfWeek.value - 1] else dia.dayOfMonth.toString(),
                        km = del.sumOf { it.distanciaM } / 1000,
                        minutos = del.sumOf { it.duracionMs } / 60_000.0,
                    )
                }
            }
            PeriodoHistorial.Anio -> {
                val porMes = lista.groupBy { YearMonth.from(fecha(it)) }
                val actual = YearMonth.from(hoy)
                (11L downTo 0).map { atras ->
                    val mes = actual.minusMonths(atras)
                    val del = porMes[mes].orEmpty()
                    Barra(MESES[mes.monthValue - 1], del.sumOf { it.distanciaM } / 1000, del.sumOf { it.duracionMs } / 60_000.0)
                }
            }
        }
    }

    fun resumen(lista: List<Entrenamiento>) = ResumenPeriodo(
        distanciaKm = lista.sumOf { it.distanciaM } / 1000,
        salidas = lista.size,
        duracionMs = lista.sumOf { it.duracionMs },
        subidaM = lista.sumOf { it.subidaM },
    )

    /** El mejor ritmo solo cuenta en salidas de al menos 1 km (en 200 m cualquiera "vuela"). */
    fun records(lista: List<Entrenamiento>) = Records(
        masLarga = lista.filter { it.distanciaM > 0 }.maxByOrNull { it.distanciaM },
        mejorRitmo = lista.filter { it.distanciaM >= 1_000 }.minByOrNull { it.ritmoPromedioSegKm ?: Double.MAX_VALUE },
        masLargaEnTiempo = lista.filter { it.duracionMs > 0 }.maxByOrNull { it.duracionMs },
    )
}

/** "45 min" o "3,5 h" para totales de tiempo. */
fun formatoHoras(ms: Long): String {
    val minutos = ms / 60_000
    return if (minutos < 60) "$minutos min" else String.format(java.util.Locale.forLanguageTag("es-CO"), "%.1f h", minutos / 60.0)
}
