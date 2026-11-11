package com.udenar.runway.domain

import java.util.Locale
import kotlin.math.roundToLong

enum class EstadoEntrenamiento { Activo, Pausado, Finalizado }

/** Ruta que el corredor quiere seguir (planificada o de Mis rutas). Es solo una guía en el mapa. */
data class RutaReferencia(
    val rutaId: String?,
    val nombre: String,
    val puntos: List<Coordenada>,
)

/** Un entrenamiento registrado (ya terminado), listo para resumen e historial. */
data class Entrenamiento(
    val id: String,
    val rutaNombre: String?,
    val inicioEn: Long,
    val finEn: Long?,
    val distanciaM: Double,
    val duracionMs: Long,
    val subidaM: Double,
    /** Un tramo por cada vez que se reanudó tras una pausa (no se unen con una línea). */
    val segmentos: List<List<Coordenada>>,
    val altitudes: List<Double>,
    val pendienteSync: Boolean,
) {
    val puntos: List<Coordenada> get() = segmentos.flatten()
    /** Segundos por kilómetro; null si la distancia es demasiado corta para tener sentido. */
    val ritmoPromedioSegKm: Double? get() = ritmoSegKm(duracionMs, distanciaM)
}

/**
 * Filtro de ruido del GPS (plan, sección 11: "distancia mínima entre puntos para evitar saltos").
 * Decide si una lectura se registra en el trazado.
 */
object FiltroGps {
    /** Lecturas con más error que esto no se registran (y se avisa "GPS débil"). */
    const val PRECISION_MAX_M = 25f
    /** Movimientos menores son ruido de un corredor quieto. */
    const val DISTANCIA_MIN_M = 3.0
    /** 12 m/s = 43 km/h: más rápido que cualquier corredor → es un salto del GPS. */
    const val VELOCIDAD_MAX_MS = 12.0

    sealed interface Decision {
        data class Aceptar(val metros: Double) : Decision
        data object Imprecisa : Decision
        data object MuyCerca : Decision
        data object Salto : Decision
    }

    fun evaluar(anterior: PosicionGps?, nueva: PosicionGps): Decision {
        if (nueva.precisionM > PRECISION_MAX_M) return Decision.Imprecisa
        if (anterior == null) return Decision.Aceptar(0.0)
        val metros = distanciaKm(anterior.coordenada, nueva.coordenada) * 1000
        val segundos = (nueva.tiempoMs - anterior.tiempoMs) / 1000.0
        if (segundos <= 0) return Decision.MuyCerca
        if (metros / segundos > VELOCIDAD_MAX_MS) return Decision.Salto
        if (metros < DISTANCIA_MIN_M) return Decision.MuyCerca
        return Decision.Aceptar(metros)
    }
}

/** Ritmo en segundos por km; null por debajo de 50 m (sería un número sin sentido). */
fun ritmoSegKm(duracionMs: Long, distanciaM: Double): Double? =
    if (distanciaM < 50) null else (duracionMs / 1000.0) / (distanciaM / 1000.0)

/** Cronómetro: "14:05" o "1:02:09". */
fun formatoCronometro(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

/** Ritmo "5:42" (min:seg por km) o "--:--" si no hay dato. Más de 30 min/km se considera caminar parado. */
fun formatoRitmo(segPorKm: Double?): String {
    if (segPorKm == null || segPorKm.isNaN() || segPorKm > 30 * 60) return "--:--"
    val s = segPorKm.roundToLong()
    return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
