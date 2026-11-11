package com.udenar.runway.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.udenar.runway.ui.explorar.ExplorarScreen
import com.udenar.runway.ui.inicio.InicioScreen
import com.udenar.runway.ui.navegacion.BarraInferior
import com.udenar.runway.ui.navegacion.RutaEntrenar
import com.udenar.runway.ui.navegacion.RutaExplorar
import com.udenar.runway.ui.navegacion.RutaInicio
import com.udenar.runway.ui.navegacion.RutaMisRutas
import com.udenar.runway.ui.navegacion.RutaPerfil
import com.udenar.runway.ui.navegacion.Seccion
import com.udenar.runway.ui.theme.Runway

/** Raíz de la interfaz: barra inferior + grafo de navegación de las 5 secciones. */
@Composable
fun RunwayRaiz() {
    val nav = rememberNavController()
    val entrada by nav.currentBackStackEntryAsState()
    val actual = Seccion.entries.firstOrNull { s ->
        entrada?.destination?.hierarchy?.any { it.hasRoute(s.ruta::class) } == true
    }

    fun irA(seccion: Seccion) {
        nav.navigate(seccion.ruta) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = Runway.Fondo,
        // Cada pantalla maneja la barra de estado (el mapa irá de borde a borde)
        contentWindowInsets = WindowInsets(0),
        bottomBar = { BarraInferior(actual = actual, onSeleccion = ::irA) },
    ) { relleno ->
        NavHost(
            navController = nav,
            startDestination = RutaInicio,
            modifier = Modifier.padding(relleno),
        ) {
            composable<RutaInicio> {
                InicioScreen(
                    onPlanificar = { irA(Seccion.Explorar) },
                    onEmpezar = { irA(Seccion.Entrenar) },
                    onMisRutas = { irA(Seccion.MisRutas) },
                )
            }
            composable<RutaExplorar> { ExplorarScreen() }
            composable<RutaEntrenar> {
                PantallaEnConstruccion(
                    "Entrenar",
                    Icons.AutoMirrored.Outlined.DirectionsRun,
                    "Aquí podrás iniciar, pausar y finalizar un entrenamiento con GPS, incluso sin internet.",
                )
            }
            composable<RutaMisRutas> {
                PantallaEnConstruccion(
                    "Mis rutas",
                    Icons.Outlined.Route,
                    "Aquí verás tus rutas privadas y reutilizables.",
                )
            }
            composable<RutaPerfil> {
                PantallaEnConstruccion(
                    "Perfil",
                    Icons.Outlined.Person,
                    "Aquí podrás editar tu nombre, nivel y preferencia de desnivel.",
                )
            }
        }
    }
}
