package com.udenar.runway.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.RunwayApp
import com.udenar.runway.ui.configuracion.ConfiguracionInicialScreen
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import com.udenar.runway.ui.navegacion.GrafoPlanificar
import com.udenar.runway.ui.navegacion.RutaDetalleRuta
import com.udenar.runway.ui.navegacion.RutaPlanificar
import com.udenar.runway.ui.navegacion.RutaResultados
import com.udenar.runway.ui.navegacion.RutaRutaGuardada
import com.udenar.runway.ui.rutas.MisRutasScreen
import com.udenar.runway.ui.rutas.RutaGuardadaScreen
import com.udenar.runway.ui.planificar.DetalleRutaScreen
import com.udenar.runway.ui.planificar.PlanificadorViewModel
import com.udenar.runway.ui.planificar.PlanificarScreen
import com.udenar.runway.ui.planificar.ResultadosScreen
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

/**
 * Raíz de la interfaz. Sin perfil guardado muestra la configuración inicial (5.1);
 * con perfil, la barra inferior y las 5 secciones.
 */
@Composable
fun RunwayRaiz() {
    val contenedor = (LocalContext.current.applicationContext as RunwayApp).contenedor
    val perfil by contenedor.preferenciasPerfil.perfil.collectAsStateWithLifecycle()
    val actual = perfil
    if (actual == null) {
        ConfiguracionInicialScreen()
    } else {
        Principal(nombre = actual.nombre)
    }
}

@Composable
private fun Principal(nombre: String) {
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
        // La barra solo se ve en las 5 secciones principales, no en pantallas internas
        bottomBar = { if (actual != null) BarraInferior(actual = actual, onSeleccion = ::irA) },
    ) { relleno ->
        NavHost(
            navController = nav,
            startDestination = RutaInicio,
            modifier = Modifier.padding(relleno),
        ) {
            composable<RutaInicio> {
                InicioScreen(
                    onPlanificar = { nav.navigate(GrafoPlanificar()) },
                    onEmpezar = { irA(Seccion.Entrenar) },
                    onMisRutas = { irA(Seccion.MisRutas) },
                    nombre = nombre,
                )
            }
            composable<RutaExplorar> {
                ExplorarScreen(onPlanificar = { lugar -> nav.navigate(GrafoPlanificar.desde(lugar)) })
            }

            // Planificar → Resultados → Detalle comparten un ViewModel del grafo
            navigation<GrafoPlanificar>(startDestination = RutaPlanificar) {
                composable<RutaPlanificar> { entrada ->
                    val vm = vmPlanificador(nav, entrada)
                    PlanificarScreen(vm, onVolver = { nav.popBackStack() }, onBuscar = { nav.navigate(RutaResultados) })
                }
                composable<RutaResultados> { entrada ->
                    val vm = vmPlanificador(nav, entrada)
                    ResultadosScreen(vm, onVolver = { nav.popBackStack() }, onDetalle = { nav.navigate(RutaDetalleRuta) })
                }
                composable<RutaDetalleRuta> { entrada ->
                    val vm = vmPlanificador(nav, entrada)
                    DetalleRutaScreen(vm, onVolver = { nav.popBackStack() }, onVerMisRutas = { irA(Seccion.MisRutas) })
                }
            }
            composable<RutaEntrenar> {
                PantallaEnConstruccion(
                    "Entrenar",
                    Icons.AutoMirrored.Outlined.DirectionsRun,
                    "Aquí podrás iniciar, pausar y finalizar un entrenamiento con GPS, incluso sin internet.",
                )
            }
            composable<RutaMisRutas> {
                MisRutasScreen(
                    onAbrir = { id -> nav.navigate(RutaRutaGuardada(id)) },
                    onPlanificar = { nav.navigate(GrafoPlanificar()) },
                )
            }
            composable<RutaRutaGuardada> { RutaGuardadaScreen(onVolver = { nav.popBackStack() }) }
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

@Composable
private fun vmPlanificador(nav: NavHostController, entrada: NavBackStackEntry): PlanificadorViewModel {
    val grafo = remember(entrada) { nav.getBackStackEntry<GrafoPlanificar>() }
    return viewModel(grafo, factory = PlanificadorViewModel.Factory)
}
