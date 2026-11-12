package com.udenar.runway.data.amigos

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Una relación con otra persona: amistad aceptada o solicitud pendiente. */
@Serializable
data class Amigo(
    @SerialName("amistad_id") val amistadId: Long,
    @SerialName("amigo_id") val amigoId: String,
    val nombre: String,
    val avatar: String,
    val estado: String,
    @SerialName("la_envie_yo") val laEnvieYo: Boolean,
) {
    val aceptada: Boolean get() = estado == "aceptada"
}

/**
 * Amigos por código. Todo pasa por funciones del servidor (RPC) que validan quién llama,
 * así nadie puede ver ni modificar relaciones ajenas.
 */
class RepositorioAmigos(private val supabase: SupabaseClient) {

    suspend fun lista(): List<Amigo> = supabase.postgrest.rpc("mis_amigos").decodeList()

    /** Envía una solicitud con el código del amigo. Devuelve un mensaje para mostrar. */
    suspend fun solicitar(codigo: String): String {
        val r = supabase.postgrest.rpc("solicitar_amistad", buildJsonObject { put("p_codigo", codigo.trim().uppercase()) })
            .decodeAs<String>()
        return mensajeSolicitud(r)
    }

    suspend fun responder(amistadId: Long, aceptar: Boolean) {
        supabase.postgrest.rpc("responder_solicitud", buildJsonObject {
            put("p_id", amistadId)
            put("p_aceptar", aceptar)
        })
    }

    suspend fun eliminar(amistadId: Long) {
        supabase.postgrest.rpc("eliminar_amistad", buildJsonObject { put("p_id", amistadId) })
    }

    companion object {
        fun mensajeSolicitud(r: String): String = when (r) {
            "enviada" -> "Solicitud enviada. Cuando la acepte, se verán en el mapa mientras corren."
            "aceptada" -> "¡Ahora son amigos! Esa persona ya te había enviado una solicitud."
            "ya_amigos" -> "Ya son amigos."
            "ya_enviada" -> "Ya le enviaste una solicitud; falta que la acepte."
            "eres_tu" -> "Ese es tu propio código."
            "no_existe" -> "No encontramos a nadie con ese código. Revísalo."
            else -> "No se pudo enviar la solicitud. Intenta de nuevo."
        }
    }
}
