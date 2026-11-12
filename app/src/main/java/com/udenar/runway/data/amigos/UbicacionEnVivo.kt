package com.udenar.runway.data.amigos

import com.udenar.runway.data.preferencias.PreferenciasPerfil
import com.udenar.runway.domain.Coordenada
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime

/** Fila de `ubicaciones_en_vivo`: alguien que está entrenando ahora mismo. */
@Serializable
data class UbicacionRemota(
    @SerialName("usuario_id") val usuarioId: String,
    val nombre: String,
    val avatar: String,
    val lat: Double,
    val lon: Double,
    @SerialName("distancia_m") val distanciaM: Double = 0.0,
    @SerialName("inicio_en") val inicioEn: Long,
    @SerialName("actualizada_en") val actualizadaEn: String? = null,
)

/** Amigo corriendo, listo para el mapa. */
data class AmigoEnVivo(
    val id: String,
    val nombre: String,
    val avatar: String,
    val coordenada: Coordenada,
    val distanciaM: Double,
    val inicioEn: Long,
    /** Hora del teléfono en que llegó la última posición (para descartar las viejas). */
    val recibidaEn: Long,
)

/**
 * Publica la posición propia SOLO mientras el entrenamiento está activo (decisión de privacidad).
 * Se limita a una escritura cada 5 s para cuidar batería y datos.
 */
class PublicadorUbicacion(
    private val supabase: SupabaseClient,
    private val perfiles: PreferenciasPerfil,
) {
    private val bloqueo = Mutex()
    private var ultimaPublicacion = 0L

    suspend fun publicar(c: Coordenada, distanciaM: Double, inicioEn: Long) {
        val perfil = perfiles.perfil.value ?: return
        if (supabase.auth.currentUserOrNull() == null) return
        bloqueo.withLock {
            val ahora = System.currentTimeMillis()
            if (ahora - ultimaPublicacion < 5_000) return
            ultimaPublicacion = ahora
        }
        runCatching {
            supabase.from("ubicaciones_en_vivo").upsert(
                UbicacionRemota(perfil.usuarioId, perfil.nombre, perfil.avatar, c.lat, c.lon, distanciaM, inicioEn),
            )
        }
    }

    /** Al pausar o terminar: la ubicación deja de existir para los amigos. */
    suspend fun retirar() {
        val id = supabase.auth.currentUserOrNull()?.id ?: return
        bloqueo.withLock { ultimaPublicacion = 0 }
        runCatching { supabase.from("ubicaciones_en_vivo").delete { filter { eq("usuario_id", id) } } }
    }
}

/**
 * Amigos corriendo en tiempo real con Supabase Realtime.
 *
 * 1. Lee las ubicaciones visibles (la seguridad por filas solo deja ver las de amigos aceptados).
 * 2. Se suscribe a los cambios de la tabla por WebSocket: inserciones y actualizaciones mueven
 *    el marcador; los borrados (pausa o fin) lo quitan.
 * 3. Si alguien deja de enviar posiciones por más de 2 minutos (p. ej. se quedó sin señal),
 *    se oculta.
 */
class AmigosEnVivo(private val supabase: SupabaseClient) {

    @OptIn(DelicateCoroutinesApi::class)
    fun observar(): Flow<List<AmigoEnVivo>> = callbackFlow {
        val yo = supabase.auth.currentUserOrNull()?.id
        if (yo == null) {
            trySend(emptyList())
            awaitClose()
            return@callbackFlow
        }
        val amigos = mutableMapOf<String, AmigoEnVivo>()
        val bloqueo = Mutex()
        suspend fun emitir() = bloqueo.withLock {
            val limite = System.currentTimeMillis() - VIGENCIA_MS
            amigos.values.removeAll { it.recibidaEn < limite }
            trySend(amigos.values.sortedBy { it.nombre })
        }
        fun aAmigo(u: UbicacionRemota) = AmigoEnVivo(
            u.usuarioId, u.nombre, u.avatar, Coordenada(u.lat, u.lon), u.distanciaM, u.inicioEn,
            // La hora del servidor sirve para descartar filas viejas que quedaron de un cierre inesperado
            recibidaEn = u.actualizadaEn?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                ?.coerceAtMost(System.currentTimeMillis()) ?: System.currentTimeMillis(),
        )

        val canal = supabase.channel("amigos-en-vivo")
        val cambios = canal.postgresChangeFlow<PostgresAction>(schema = "public") { table = "ubicaciones_en_vivo" }
        launch {
            cambios.collect { accion ->
                when (accion) {
                    is PostgresAction.Insert -> accion.decodeRecord<UbicacionRemota>()
                    is PostgresAction.Update -> accion.decodeRecord<UbicacionRemota>()
                    is PostgresAction.Delete -> {
                        val id = accion.oldRecord["usuario_id"]?.jsonPrimitive?.content
                        bloqueo.withLock { if (id != null) amigos.remove(id) }
                        null
                    }
                    else -> null
                }?.takeIf { it.usuarioId != yo }?.let { u ->
                    bloqueo.withLock { amigos[u.usuarioId] = aAmigo(u).copy(recibidaEn = System.currentTimeMillis()) }
                }
                emitir()
            }
        }
        canal.subscribe()

        // Estado inicial
        runCatching {
            supabase.from("ubicaciones_en_vivo").select().decodeList<UbicacionRemota>()
        }.getOrDefault(emptyList()).filter { it.usuarioId != yo }.forEach { u ->
            bloqueo.withLock { amigos.putIfAbsent(u.usuarioId, aAmigo(u)) }
        }
        emitir()

        // Limpieza periódica de posiciones viejas
        launch {
            while (true) {
                delay(15_000)
                emitir()
            }
        }

        // Al dejar de observar (salir de Explorar) se cierra el canal de tiempo real
        awaitClose { GlobalScope.launch { runCatching { supabase.realtime.removeChannel(canal) } } }
    }

    private companion object {
        const val VIGENCIA_MS = 2 * 60_000L
    }
}
