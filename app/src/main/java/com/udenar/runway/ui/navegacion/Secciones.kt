package com.udenar.runway.ui.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.ui.graphics.vector.ImageVector
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Lugar
import kotlinx.serialization.Serializable

/** Rutas de navegación con tipos (navigation-compose). */
@Serializable data object RutaInicio
@Serializable data object RutaExplorar
@Serializable data object RutaEntrenar
@Serializable data object RutaMisRutas
@Serializable data object RutaPerfil

/**
 * Grafo del planificador (Planificar → Resultados → Detalle). Recibe opcionalmente
 * el punto de inicio elegido en el mapa. Las coordenadas viajan como texto porque la
 * navegación no admite Double nulos.
 */
@Serializable
data class GrafoPlanificar(
    val lat: String? = null,
    val lon: String? = null,
    val nombre: String? = null,
    val detalle: String? = null,
    val destinoLat: String? = null,
    val destinoLon: String? = null,
    val destinoNombre: String? = null,
    val destinoDetalle: String? = null,
) {
    fun lugar(): Lugar? {
        val la = lat?.toDoubleOrNull() ?: return null
        val lo = lon?.toDoubleOrNull() ?: return null
        return Lugar(nombre ?: "Punto del mapa", detalle.orEmpty(), Coordenada(la, lo))
    }

    fun destino(): Lugar? {
        val la = destinoLat?.toDoubleOrNull() ?: return null
        val lo = destinoLon?.toDoubleOrNull() ?: return null
        return Lugar(destinoNombre ?: "Destino", destinoDetalle.orEmpty(), Coordenada(la, lo))
    }

    companion object {
        fun desde(lugar: Lugar?) = if (lugar == null) GrafoPlanificar() else GrafoPlanificar(
            lugar.coordenada.lat.toString(),
            lugar.coordenada.lon.toString(),
            lugar.nombre,
            lugar.detalle,
        )

        /** Planificar desde mi ubicación hasta [destino]. */
        fun hasta(destino: Lugar) = GrafoPlanificar(
            destinoLat = destino.coordenada.lat.toString(),
            destinoLon = destino.coordenada.lon.toString(),
            destinoNombre = destino.nombre,
            destinoDetalle = destino.detalle,
        )
    }
}

/** Selector de punto con pin fijo al centro ("inicio" o "destino"). */
@Serializable data class RutaElegirPunto(val objetivo: String)

@Serializable data object RutaPlanificar
@Serializable data object RutaResultados
@Serializable data object RutaDetalleRuta

/** Detalle de una ruta de Mis rutas. */
@Serializable data class RutaRutaGuardada(val id: String)

/** Entrenamiento activo (pantalla completa, sin barra inferior). */
@Serializable data object RutaEntrenamientoActivo

/** Resumen de un entrenamiento terminado. */
@Serializable data class RutaResumenEntrenamiento(val id: String, val recienTerminado: Boolean = true)

/** Amigos (agregar por código, solicitudes, lista). */
@Serializable data object RutaAmigos

/** Zonas de mapa descargadas para usar sin conexión. */
@Serializable data object RutaMapasOffline

/** Editar nombre, avatar, nivel y terreno. */
@Serializable data object RutaEditarPerfil

/** Historial y estadísticas. */
@Serializable data object RutaHistorial

/** Las 5 secciones de la barra inferior (especificación, sección 4). */
enum class Seccion(val etiqueta: String, val icono: ImageVector, val ruta: Any) {
    Inicio("Inicio", Icons.Outlined.Home, RutaInicio),
    Explorar("Explorar", Icons.Outlined.Map, RutaExplorar),
    Entrenar("Entrenar", Icons.AutoMirrored.Outlined.DirectionsRun, RutaEntrenar),
    MisRutas("Mis rutas", Icons.Outlined.Route, RutaMisRutas),
    Perfil("Perfil", Icons.Outlined.Person, RutaPerfil),
}
