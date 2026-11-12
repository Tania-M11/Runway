package com.udenar.runway.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.local.EntrenamientosDao
import com.udenar.runway.data.local.RutasDao
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.TimeUnit

/**
 * Sincronización diferida con Supabase (plan 5.2 y flujo 6.2 de la especificación).
 *
 * - **Subir:** todo lo marcado `pendienteSync` (rutas nuevas, editadas o eliminadas y
 *   entrenamientos terminados) se envía con *upsert*; como el id es un UUID del teléfono,
 *   reintentar nunca duplica filas.
 * - **Descargar:** al entrar con una cuenta en un teléfono sin datos, se trae su historial.
 * - WorkManager ejecuta [TrabajoSincronizacion] solo con conexión y reintenta si falla.
 */
class Sincronizador(
    private val supabase: SupabaseClient,
    private val rutas: RutasDao,
    private val entrenamientos: EntrenamientosDao,
) {
    /** Cantidad de elementos pendientes (para los avisos "pendiente de sincronizar"). */
    val pendientes: Flow<Int> = combine(rutas.contarPendientes(), entrenamientos.contarPendientes()) { a, b -> a + b }

    private fun haySesion() = supabase.auth.currentUserOrNull() != null

    /** Sube lo pendiente. Devuelve true si no quedó nada por subir. */
    suspend fun subir(): Boolean {
        if (!haySesion()) return false
        for (r in rutas.pendientes()) {
            supabase.from("rutas").upsert(r.aRemota())
            if (r.eliminada) rutas.borrarEliminada(r.id) else rutas.marcarSincronizada(r.id, r.actualizadaEn)
        }
        for (e in entrenamientos.pendientes()) {
            supabase.from("entrenamientos").upsert(e.aRemoto(entrenamientos.puntos(e.id)))
            entrenamientos.marcarSincronizado(e.id)
        }
        return rutas.pendientes().isEmpty() && entrenamientos.pendientes().isEmpty()
    }

    /** Trae las rutas y entrenamientos de la cuenta si el teléfono no tiene ninguno. */
    suspend fun descargarSiEstaVacio() {
        if (!haySesion() || rutas.total() > 0 || entrenamientos.total() > 0) return
        supabase.from("rutas").select { filter { eq("eliminada", false) } }
            .decodeList<RutaRemota>()
            .forEach { rutas.insertar(it.aEntidad()) }
        supabase.from("entrenamientos").select { filter { eq("eliminado", false) } }
            .decodeList<EntrenamientoRemoto>()
            .forEach { remoto ->
                val (e, puntos) = remoto.aEntidad()
                entrenamientos.insertarDescargado(e, puntos)
            }
    }

    companion object {
        private const val NOMBRE = "sincronizacion"

        /** Programa una sincronización: se ejecuta cuando haya red (Wi-Fi o datos). */
        fun programar(context: Context) {
            val trabajo = OneTimeWorkRequestBuilder<TrabajoSincronizacion>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // Si ya hay una en curso, esta se encadena después (así no se pierde un cambio reciente)
            WorkManager.getInstance(context).enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.APPEND_OR_REPLACE, trabajo)
        }
    }
}

/** Trabajo en segundo plano de WorkManager: sobrevive a cierres de la app y reinicios. */
class TrabajoSincronizacion(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sincronizador = (applicationContext as RunwayApp).contenedor.sincronizador
        return try {
            if (sincronizador.subir()) Result.success() else Result.retry()
        } catch (e: Exception) {
            if (runAttemptCount < 8) Result.retry() else Result.failure()
        }
    }
}
