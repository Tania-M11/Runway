package com.udenar.runway.ui.navegacion

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

/** Rutas de navegación con tipos (navigation-compose). */
@Serializable data object RutaInicio
@Serializable data object RutaExplorar
@Serializable data object RutaEntrenar
@Serializable data object RutaMisRutas
@Serializable data object RutaPerfil

/** Las 5 secciones de la barra inferior (especificación, sección 4). */
enum class Seccion(val etiqueta: String, val icono: ImageVector, val ruta: Any) {
    Inicio("Inicio", Icons.Outlined.Home, RutaInicio),
    Explorar("Explorar", Icons.Outlined.Map, RutaExplorar),
    Entrenar("Entrenar", Icons.AutoMirrored.Outlined.DirectionsRun, RutaEntrenar),
    MisRutas("Mis rutas", Icons.Outlined.Route, RutaMisRutas),
    Perfil("Perfil", Icons.Outlined.Person, RutaPerfil),
}
