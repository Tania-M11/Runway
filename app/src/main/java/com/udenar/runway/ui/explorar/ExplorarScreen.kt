package com.udenar.runway.ui.explorar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.domain.Lugar
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.BarraBusqueda
import com.udenar.runway.ui.components.EstadoCargando
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.components.FilaLugar
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import com.udenar.runway.ui.ubicacion.abrirAjustesDeLaApp
import com.udenar.runway.ui.ubicacion.abrirAjustesDeUbicacion
import com.udenar.runway.ui.ubicacion.rememberPedirPermisoUbicacion

/** Explorar (especificación 5.3): mapa de cualquier lugar, buscador y punto seleccionado. */
@Composable
fun ExplorarScreen(
    onPlanificar: (Lugar?) -> Unit,
    vm: ExplorarViewModel = viewModel(factory = ExplorarViewModel.Factory),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    val densidad = LocalDensity.current
    // La brújula aparece bajo el buscador (barra de estado + buscador + margen)
    val margenBrujula = WindowInsets.statusBars.getTop(densidad) + with(densidad) { 92.dp.roundToPx() }
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current

    // Mecanismo 1: el GPS se escucha solo mientras la pantalla está visible y hay permiso
    LaunchedEffect(estado.permisoUbicacion) {
        if (estado.permisoUbicacion) ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.seguirUbicacion() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.actualizarPermiso() }
    val pedirPermiso = rememberPedirPermisoUbicacion(vm::onResultadoPermiso)

    val onCentrar: () -> Unit = {
        when {
            estado.permisoUbicacion -> vm.centrarEnMi()
            estado.aviso == AvisoUbicacion.PermisoBloqueado -> context.abrirAjustesDeLaApp()
            else -> pedirPermiso()
        }
    }

    Box(Modifier.fillMaxSize()) {
        MapaRunway(
            camaraInicial = estado.camara,
            puntoSeleccionado = estado.seleccionado?.coordenada,
            miPosicion = estado.miPosicion?.coordenada,
            comandos = vm.comandos,
            intentoCarga = estado.intentoCarga,
            onEstiloCargado = vm::onEstiloCargado,
            onErrorCarga = { vm.onErrorMapa() },
            onCamaraQuieta = vm::onCamaraQuieta,
            onToque = {
                foco.clearFocus()
                vm.onToqueMapa(it)
            },
            margenSuperiorBrujula = margenBrujula,
            modifier = Modifier.fillMaxSize(),
        )

        // Parte superior: buscador, resultados y avisos
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Espacio.l, vertical = Espacio.m),
            verticalArrangement = Arrangement.spacedBy(Espacio.s),
        ) {
            BarraBusqueda(
                texto = estado.consulta,
                onTexto = vm::onConsulta,
                sugerencia = "Buscar ciudad, barrio o parque",
                onLimpiar = vm::limpiarBusqueda,
            )
            PanelResultados(
                estado = estado,
                onElegir = {
                    foco.clearFocus()
                    vm.elegirLugar(it)
                },
            )
            if (!estado.conectado) {
                AvisoEstado(TipoAviso.SinConexion, "Sin conexión: verás solo lo que el mapa ya cargó")
            } else if (!estado.mapaListo && !estado.errorMapa) {
                PildoraCargando("Cargando mapa…")
            }
            when (estado.aviso) {
                AvisoUbicacion.PermisoNegado -> AvisoEstado(
                    TipoAviso.SinUbicacion,
                    "Sin permiso de ubicación. Toca el mapa para elegir un punto.",
                    accion = "Permitir",
                    onAccion = pedirPermiso,
                )
                AvisoUbicacion.PermisoBloqueado -> AvisoEstado(
                    TipoAviso.SinUbicacion,
                    "Sin permiso de ubicación. Actívalo en Ajustes o toca el mapa.",
                    accion = "Ajustes",
                    onAccion = { context.abrirAjustesDeLaApp() },
                )
                AvisoUbicacion.UbicacionApagada -> AvisoEstado(
                    TipoAviso.SinUbicacion,
                    "La ubicación del teléfono está apagada.",
                    accion = "Activar",
                    onAccion = { context.abrirAjustesDeUbicacion() },
                )
                null -> if (estado.buscandoMiPosicion) PildoraCargando("Buscando tu ubicación…")
            }
        }

        // Error de carga cartográfica con opción de reintentar
        if (estado.errorMapa) {
            TarjetaRunway(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(Espacio.xl),
            ) {
                EstadoVacio(
                    icono = Icons.Outlined.Map,
                    titulo = "No se pudo cargar el mapa",
                    texto = "Revisa tu conexión a internet. Lo intentaremos de nuevo cuando vuelva.",
                    accion = "Reintentar",
                    onAccion = vm::reintentarMapa,
                )
            }
        }

        // Parte inferior: centrar en mí + lugar seleccionado (deja libre la atribución © OpenStreetMap)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = Espacio.l, end = Espacio.l, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Acceso al planificador (especificación 5.3)
                if (estado.seleccionado == null) {
                    Surface(shape = RoundedCornerShape(50), shadowElevation = 6.dp, color = Runway.Lima) {
                        BotonRunway(
                            "Planificar ruta",
                            onClick = { onPlanificar(null) },
                            estilo = EstiloBoton.Lima,
                            icono = Icons.Outlined.Route,
                            alto = 52.dp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                BotonCircular(
                    icono = if (estado.permisoUbicacion) Icons.Outlined.MyLocation else Icons.Outlined.LocationSearching,
                    descripcion = "Centrar en mi ubicación",
                    onClick = onCentrar,
                    tamano = 52.dp,
                    sombra = 6.dp,
                    colorIcono = Runway.MoradoOscuro,
                )
            }
            AnimatedVisibility(
                visible = estado.seleccionado != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                estado.seleccionado?.let { lugar ->
                    TarjetaLugar(lugar, onCerrar = vm::quitarSeleccion, onPlanificar = { onPlanificar(lugar) })
                }
            }
        }
    }
}

@Composable
private fun PildoraCargando(texto: String) {
    Surface(shape = RoundedCornerShape(50), color = Runway.Superficie, shadowElevation = 2.dp) {
        EstadoCargando(texto, Modifier.padding(horizontal = Espacio.l).width(220.dp))
    }
}

@Composable
private fun PanelResultados(estado: ExplorarEstado, onElegir: (Lugar) -> Unit) {
    val visible = estado.buscando || estado.resultados.isNotEmpty() || estado.mensajeBusqueda != null
    if (!visible) return
    Surface(
        shape = RoundedCornerShape(Espacio.radioPequeno + 8.dp),
        color = Runway.Superficie,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when {
            estado.buscando -> EstadoCargando("Buscando lugares…")
            estado.mensajeBusqueda != null -> Text(
                estado.mensajeBusqueda,
                style = MaterialTheme.typography.bodyMedium,
                color = Runway.TextoSecundario,
                modifier = Modifier.padding(Espacio.l),
            )
            else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(estado.resultados) { lugar ->
                    FilaLugar(lugar, onClick = { onElegir(lugar) })
                    if (lugar != estado.resultados.last()) {
                        HorizontalDivider(color = Runway.Borde, modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaLugar(lugar: Lugar, onCerrar: () -> Unit, onPlanificar: () -> Unit) {
    TarjetaRunway(modifier = Modifier.fillMaxWidth(), relleno = Espacio.l) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Espacio.radioPequeno), color = Runway.LavandaClaro, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.LocationOn, null, tint = Runway.Morado, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Text(lugar.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    lugar.detalle.ifBlank { lugar.coordenada.texto() },
                    style = MaterialTheme.typography.bodySmall,
                    color = Runway.TextoSecundario,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCerrar) {
                Icon(Icons.Outlined.Close, contentDescription = "Quitar punto", tint = Runway.TextoSecundario)
            }
        }
        Spacer(Modifier.height(Espacio.m))
        BotonRunway(
            "Planificar desde aquí",
            onClick = onPlanificar,
            icono = Icons.Outlined.Route,
            modifier = Modifier.fillMaxWidth(),
            alto = 48.dp,
        )
    }
}
