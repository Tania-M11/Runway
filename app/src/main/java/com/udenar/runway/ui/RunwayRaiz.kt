package com.udenar.runway.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.udenar.runway.R
import com.udenar.runway.ui.acceso.AccesoScreen
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.perfil.PerfilScreen
import com.udenar.runway.ui.theme.Espacio
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
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
import com.udenar.runway.ui.entrenar.EntrenarScreen
import com.udenar.runway.ui.entrenar.EntrenamientoActivoScreen
import com.udenar.runway.ui.entrenar.ResumenEntrenamientoScreen
import com.udenar.runway.ui.navegacion.RutaEntrenamientoActivo
import com.udenar.runway.ui.navegacion.RutaResumenEntrenamiento
import com.udenar.runway.ui.navegacion.RutaHistorial
import com.udenar.runway.ui.historial.HistorialScreen
import com.udenar.runway.ui.inicio.DatosInicio
import com.udenar.runway.ui.inicio.datosInicio
import androidx.navigation.toRoute
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
    val sesion by contenedor.cuenta.estadoSesion.collectAsStateWithLifecycle()
    val perfil by contenedor.preferenciasPerfil.perfil.collectAsStateWithLifecycle()

    when (val s = sesion) {
        is SessionStatus.Initializing -> PantallaCarga()
        is SessionStatus.NotAuthenticated -> AccesoScreen()
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> {
            // RefreshFailure = no se pudo renovar la sesión (p. ej. sin internet): se sigue con los datos locales
            val usuarioId = (s as? SessionStatus.Authenticated)?.session?.user?.id ?: perfil?.usuarioId
            var intento by remember { mutableStateOf(0) }
            var fallo by remember { mutableStateOf(false) }
            LaunchedEffect(usuarioId, intento) {
                fallo = false
                if (s is SessionStatus.Authenticated) fallo = !contenedor.cuenta.prepararSesion()
            }
            val p = perfil
            when {
                p != null && p.usuarioId == usuarioId && !p.configurado -> ConfiguracionInicialScreen(nombre = p.nombre, avatar = p.avatar)
                p != null && p.usuarioId == usuarioId -> Principal(nombre = p.nombre, avatar = p.avatar)
                fallo -> ErrorPerfil(onReintentar = { intento++ })
                else -> PantallaCarga()
            }
        }
    }
}

@Composable
private fun PantallaCarga() {
    Box(Modifier.fillMaxSize().background(Runway.Fondo), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.logo_runway), contentDescription = "RUNWAY", modifier = Modifier.fillMaxWidth(0.6f))
    }
}

/** No se pudo traer el perfil del servidor y no hay copia local de esta cuenta. */
@Composable
private fun ErrorPerfil(onReintentar: () -> Unit) {
    val contenedor = (LocalContext.current.applicationContext as RunwayApp).contenedor
    val alcance = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Runway.Fondo).padding(Espacio.margen), contentAlignment = Alignment.Center) {
        EstadoVacio(
            icono = Icons.Outlined.CloudOff,
            titulo = "No pudimos cargar tu perfil",
            texto = "Revisa tu conexión a internet. Necesitamos traer tu perfil la primera vez que entras en este teléfono.",
            accion = "Reintentar",
            onAccion = onReintentar,
        )
        TextButton(
            onClick = { alcance.launch { contenedor.cuenta.cerrarSesion() } },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        ) { Text("Cerrar sesión", color = Runway.TextoSecundario) }
    }
}

@Composable
private fun Principal(nombre: String, avatar: String) {
    val contenedor = (LocalContext.current.applicationContext as RunwayApp).contenedor
    val sesion by contenedor.sesion.estado.collectAsStateWithLifecycle()
    val datosInicio by remember { datosInicio(contenedor.entrenamientos) }.collectAsStateWithLifecycle(DatosInicio())
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
                    avatar = avatar,
                    entrenamientoEnCurso = sesion.enCurso,
                    onVolverEntrenamiento = { nav.navigate(RutaEntrenamientoActivo) { launchSingleTop = true } },
                    datos = datosInicio,
                    onHistorial = { nav.navigate(RutaHistorial) },
                    onAbrirEntrenamiento = { id -> nav.navigate(RutaResumenEntrenamiento(id, recienTerminado = false)) },
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
                    DetalleRutaScreen(
                        vm,
                        onVolver = { nav.popBackStack() },
                        onVerMisRutas = { irA(Seccion.MisRutas) },
                        onEntrenar = { nav.navigate(RutaEntrenamientoActivo) { launchSingleTop = true } },
                    )
                }
            }
            composable<RutaEntrenar> {
                EntrenarScreen(onAbrirEntrenamiento = { nav.navigate(RutaEntrenamientoActivo) { launchSingleTop = true } })
            }
            composable<RutaEntrenamientoActivo> {
                EntrenamientoActivoScreen(
                    onSalir = { nav.popBackStack() },
                    onFinalizado = { id ->
                        nav.navigate(RutaResumenEntrenamiento(id)) {
                            popUpTo<RutaEntrenamientoActivo> { inclusive = true }
                        }
                    },
                )
            }
            composable<RutaResumenEntrenamiento> { entrada ->
                val nuevo = entrada.toRoute<RutaResumenEntrenamiento>().recienTerminado
                ResumenEntrenamientoScreen(onListo = { if (nuevo) irA(Seccion.Inicio) else nav.popBackStack() })
            }
            composable<RutaHistorial> {
                HistorialScreen(
                    onVolver = { nav.popBackStack() },
                    onAbrir = { id -> nav.navigate(RutaResumenEntrenamiento(id, recienTerminado = false)) },
                    onEntrenar = { irA(Seccion.Entrenar) },
                )
            }
            composable<RutaMisRutas> {
                MisRutasScreen(
                    onAbrir = { id -> nav.navigate(RutaRutaGuardada(id)) },
                    onPlanificar = { nav.navigate(GrafoPlanificar()) },
                )
            }
            composable<RutaRutaGuardada> {
                RutaGuardadaScreen(
                    onVolver = { nav.popBackStack() },
                    onEntrenar = { nav.navigate(RutaEntrenamientoActivo) { launchSingleTop = true } },
                )
            }
            composable<RutaPerfil> { PerfilScreen() }
        }
    }
}

@Composable
private fun vmPlanificador(nav: NavHostController, entrada: NavBackStackEntry): PlanificadorViewModel {
    val grafo = remember(entrada) { nav.getBackStackEntry<GrafoPlanificar>() }
    return viewModel(grafo, factory = PlanificadorViewModel.Factory)
}
