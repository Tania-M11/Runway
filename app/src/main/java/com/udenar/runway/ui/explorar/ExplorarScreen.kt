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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Flag
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.CloudDownload
import com.udenar.runway.ui.offline.comoSuperposiciones
import com.udenar.runway.ui.offline.formatoMegas
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
import com.udenar.runway.data.amigos.AmigoEnVivo
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.ui.components.AvatarImagen
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.mapa.MarcadorAmigo
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import com.udenar.runway.ui.ubicacion.abrirAjustesDeLaApp
import com.udenar.runway.ui.ubicacion.abrirAjustesDeUbicacion
import com.udenar.runway.ui.ubicacion.rememberPedirPermisoUbicacion

/** Explorar (especificación 5.3): mapa de cualquier lugar, buscador y punto seleccionado. */
@Composable
fun ExplorarScreen(
    onPlanificar: (Lugar?) -> Unit,
    onLlegarHasta: (Lugar) -> Unit,
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
    // Amigos corriendo: el canal de tiempo real se abre solo con la pantalla visible
    LaunchedEffect(Unit) { ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.seguirAmigos() } }
    val pedirPermiso = rememberPedirPermisoUbicacion(vm::onResultadoPermiso)

    var confirmarDescarga by remember { mutableStateOf(false) }
    if (confirmarDescarga) {
        val teselas = estado.teselasVista
        val grande = teselas > com.udenar.runway.domain.Teselas.LIMITE
        AlertDialog(
            onDismissRequest = { confirmarDescarga = false },
            containerColor = Runway.Fondo,
            title = { Text("Mapa sin conexión") },
            text = {
                Text(
                    if (grande) "Lo que ves es demasiado grande para descargar ($teselas teselas). Acércate un poco y vuelve a intentarlo."
                    else "Se guardará el mapa de lo que ves en pantalla (≈ ${"%.0f".format(com.udenar.runway.domain.Teselas.megasEstimados(teselas))} MB) " +
                        "para verlo aunque no tengas señal. Puedes borrarlo en Perfil → Mapas sin conexión.",
                )
            },
            confirmButton = {
                if (!grande) TextButton(onClick = {
                    confirmarDescarga = false
                    vm.descargarVista()
                }) { Text("Descargar", color = Runway.Morado) }
            },
            dismissButton = { TextButton(onClick = { confirmarDescarga = false }) { Text(if (grande) "Entendido" else "Cancelar", color = Runway.TextoSecundario) } },
        )
    }
    estado.mensajeDescarga?.let { m ->
        AlertDialog(
            onDismissRequest = vm::cerrarMensajeDescarga,
            containerColor = Runway.Fondo,
            text = { Text(m) },
            confirmButton = { TextButton(onClick = vm::cerrarMensajeDescarga) { Text("Entendido", color = Runway.Morado) } },
        )
    }

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
            amigos = estado.amigos.map { MarcadorAmigo(it.id, it.nombre, it.avatar, it.coordenada) },
            superposiciones = estado.zonasOffline.comoSuperposiciones(),
            onLimitesVisibles = vm::onVista,
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
            if (estado.amigos.isNotEmpty() && estado.consulta.isEmpty()) {
                FilaAmigosCorriendo(estado.amigos, onClick = vm::irAAmigo)
            }
            PanelResultados(
                estado = estado,
                onElegir = {
                    foco.clearFocus()
                    vm.elegirLugar(it)
                },
            )
            if (!estado.conectado) {
                if (estado.sobreZonaDescargada) AvisoEstado(TipoAviso.Sincronizado, "Sin conexión · usando el mapa descargado")
                else AvisoEstado(TipoAviso.SinConexion, "Sin conexión: verás solo lo que el mapa ya cargó")
            }
            estado.progresoDescarga?.takeIf { !it.terminada }?.let { p ->
                Surface(shape = RoundedCornerShape(50), color = Runway.Superficie, shadowElevation = 2.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Espacio.l, vertical = Espacio.s)) {
                        Icon(Icons.Outlined.CloudDownload, null, tint = Runway.MoradoOscuro, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Espacio.s))
                        Text(
                            "Descargando mapa… ${(p.fraccion * 100).toInt()} % · ${formatoMegas(p.bytes)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (estado.conectado && !estado.mapaListo && !estado.errorMapa) {
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
                // Descargar lo que se ve para usarlo sin conexión
                BotonCircular(
                    icono = Icons.Outlined.CloudDownload,
                    descripcion = "Descargar el mapa de esta zona",
                    onClick = { confirmarDescarga = true },
                    tamano = 52.dp,
                    sombra = 6.dp,
                    colorIcono = Runway.MoradoOscuro,
                )
                Spacer(Modifier.width(Espacio.s))
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
                    TarjetaLugar(lugar, onCerrar = vm::quitarSeleccion, onPlanificar = { onPlanificar(lugar) }, onLlegar = { onLlegarHasta(lugar) })
                }
            }
        }
    }
}

/** Amigos que están corriendo ahora: avatar, nombre y distancia; al tocar, el mapa va hacia ellos. */
@Composable
private fun FilaAmigosCorriendo(amigos: List<AmigoEnVivo>, onClick: (AmigoEnVivo) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
        items(amigos, key = { it.id }) { a ->
            Surface(onClick = { onClick(a) }, shape = RoundedCornerShape(50), color = Runway.Superficie, shadowElevation = 4.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)) {
                    AvatarImagen(a.avatar, tamano = 32.dp)
                    Spacer(Modifier.width(Espacio.s))
                    Column {
                        Text(a.nombre, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(
                            "Corriendo · ${formatoKm(a.distanciaM / 1000)} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = Runway.MoradoOscuro,
                        )
                    }
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
private fun TarjetaLugar(lugar: Lugar, onCerrar: () -> Unit, onPlanificar: () -> Unit, onLlegar: () -> Unit) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            BotonRunway(
                "Salir desde aquí",
                onClick = onPlanificar,
                icono = Icons.Outlined.Route,
                modifier = Modifier.weight(1f),
                alto = 48.dp,
            )
            BotonRunway(
                "Llegar aquí",
                onClick = onLlegar,
                estilo = EstiloBoton.Oscuro,
                icono = Icons.Outlined.Flag,
                modifier = Modifier.weight(1f),
                alto = 48.dp,
            )
        }
    }
}
