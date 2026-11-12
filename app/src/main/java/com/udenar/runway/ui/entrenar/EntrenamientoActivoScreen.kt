package com.udenar.runway.ui.entrenar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.udenar.runway.domain.Guia
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.domain.CAMARA_POR_DEFECTO
import com.udenar.runway.domain.EstadoEntrenamiento
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.formatoCronometro
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.domain.formatoRitmo
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.mapa.TrazoRuta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

private const val COLOR_TRAZADO = "#7668E8"
private const val COLOR_REFERENCIA = "#9AA3B5"
/** Tramo de la ruta ya recorrido (lima oscuro para que se vea sobre el mapa claro). */
private const val COLOR_RUTA_HECHA = "#8DB534"
/** Línea de regreso a la ruta cuando el corredor se desvía. */
private const val COLOR_REGRESO = "#D64545"

/**
 * Entrenamiento activo y pausa (especificación 5.7 y 5.8). Sin barra inferior para evitar
 * toques accidentales; botones grandes y separados.
 */
@Composable
fun EntrenamientoActivoScreen(
    onSalir: () -> Unit,
    onFinalizado: (String) -> Unit,
    vm: EntrenamientoActivoViewModel = viewModel(factory = EntrenamientoActivoViewModel.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = ui.sesion
    var confirmarFin by remember { mutableStateOf(false) }
    var confirmarDescarte by remember { mutableStateOf(false) }
    var terminando by remember { mutableStateOf(false) }

    // Si no hay sesión (p. ej. se finalizó desde otro lado), se sale
    LaunchedEffect(s.enCurso, terminando) { if (!s.enCurso && !terminando) onSalir() }
    if (!s.enCurso) return

    val pausado = s.estado == EstadoEntrenamiento.Pausado
    val camara = remember {
        val c = s.ultimaPosicion?.coordenada ?: s.referencia?.puntos?.firstOrNull()
        if (c != null) PosicionCamara(c, 16.5) else CAMARA_POR_DEFECTO
    }
    val guia = s.guia
    val trazos = buildList {
        val ref = s.referencia
        if (ref != null && guia != null) {
            // Guía en vivo: la ruta se parte en lo recorrido (lima) y lo pendiente (gris)
            val (hecho, pendiente) = Guia.partir(ref.puntos, guia.avance)
            add(TrazoRuta("referencia-pendiente", pendiente, COLOR_REFERENCIA, seleccionada = false))
            add(TrazoRuta("referencia-hecha", hecho, COLOR_RUTA_HECHA, seleccionada = true))
        } else if (ref != null) {
            add(TrazoRuta("referencia", ref.puntos, COLOR_REFERENCIA, seleccionada = false))
        }
        s.segmentos.filter { it.size > 1 }.forEachIndexed { i, tramo -> add(TrazoRuta("tramo-$i", tramo, COLOR_TRAZADO, seleccionada = true)) }
        // Desviado: línea desde donde está hasta el punto más cercano de la ruta
        val yo = s.ultimaPosicion?.coordenada
        if (guia?.fueraDeRuta == true && yo != null) add(TrazoRuta("regreso", listOf(yo, guia.proyeccion.punto), COLOR_REGRESO, seleccionada = false))
    }
    val ciclo = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.escucharBrujula() } }

    Column(Modifier.fillMaxSize()) {
        // --- Mapa con el trazado en tiempo real
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MapaRunway(
                camaraInicial = camara,
                miPosicion = s.ultimaPosicion?.coordenada,
                puntoSeleccionado = s.segmentos.firstOrNull()?.firstOrNull(),
                rutas = trazos,
                comandos = vm.comandosMapa,
                onEstiloCargado = vm::alCargarMapa,
                onArrastre = vm::dejarDeSeguir,
                // Modo guía: mapa inclinado con edificios 3D; norte arriba: plano, sin edificios
                edificios3D = ui.modoGuia,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier.statusBarsPadding().padding(Espacio.l),
                verticalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Salir (el entrenamiento sigue)", onClick = onSalir, sombra = 4.dp)
                    Spacer(Modifier.width(Espacio.s))
                    s.referencia?.let {
                        Surface(shape = CircleShape, color = Runway.Superficie, shadowElevation = 4.dp) {
                            Text(it.nombre, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Espacio.l, vertical = Espacio.m), maxLines = 1)
                        }
                    }
                }
                when {
                    guia?.llegada == true -> AvisoEstado(TipoAviso.Sincronizado, "¡Llegaste! Completaste la ruta")
                    guia?.fueraDeRuta == true -> AvisoEstado(
                        TipoAviso.Error,
                        "Te saliste de la ruta · está a ${guia.proyeccion.desvioM.toInt()} m (línea roja)",
                    )
                }
                if (ui.gpsDebil) AvisoEstado(TipoAviso.GpsDebil, "Señal GPS débil: seguimos intentando")
                if (!ui.conectado) AvisoEstado(TipoAviso.SinConexion, "Sin conexión: se guarda en el teléfono")
                if (s.recuperada) AvisoEstado(TipoAviso.PendienteSincronizar, "Recuperamos tu entrenamiento sin terminar")
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(Espacio.l),
                verticalArrangement = Arrangement.spacedBy(Espacio.s),
            ) {
                // Modo guía (mapa girado e inclinado) ↔ norte arriba
                BotonCircular(
                    if (ui.modoGuia) Icons.Outlined.Navigation else Icons.Outlined.Explore,
                    if (ui.modoGuia) "Cambiar a norte arriba" else "Cambiar a modo guía",
                    onClick = vm::alternarModoGuia,
                    sombra = 6.dp, tamano = 52.dp,
                    fondo = if (ui.modoGuia) Runway.Morado else Runway.Superficie,
                    colorIcono = if (ui.modoGuia) Runway.Lima else Runway.MoradoOscuro,
                )
                if (!ui.siguiendo) {
                    BotonCircular(
                        Icons.Outlined.MyLocation, "Seguir mi posición", onClick = vm::seguir,
                        sombra = 6.dp, tamano = 52.dp, colorIcono = Runway.MoradoOscuro,
                    )
                }
            }
        }

        // --- Panel de métricas y controles
        Surface(
            shape = RoundedCornerShape(topStart = Espacio.radioTarjeta, topEnd = Espacio.radioTarjeta),
            color = Runway.Superficie,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.navigationBarsPadding().padding(Espacio.margen)) {
                if (pausado) {
                    Surface(shape = CircleShape, color = Runway.AvisoClaro, contentColor = Runway.Aviso, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            androidx.compose.material3.Icon(Icons.Outlined.Pause, null, modifier = Modifier.padding(end = 4.dp))
                            Text("Entrenamiento pausado", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.height(Espacio.s))
                }
                // Distancia: el dato principal, en grande
                Text("Distancia", style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario, modifier = Modifier.align(Alignment.CenterHorizontally))
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(formatoKm(s.distanciaM / 1000), style = MaterialTheme.typography.displayLarge)
                    Text(" km", style = MaterialTheme.typography.titleLarge, color = Runway.TextoSecundario, modifier = Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(Espacio.m))
                Row(Modifier.fillMaxWidth()) {
                    Metrica("Tiempo", formatoCronometro(ui.duracionMs), Modifier.weight(1f))
                    Metrica("Ritmo actual", if (pausado) "--:--" else formatoRitmo(s.ritmoActualSegKm), Modifier.weight(1f), "/km")
                    Metrica("Ritmo medio", formatoRitmo(ui.ritmoPromedioSegKm), Modifier.weight(1f), "/km")
                }
                if (guia != null) {
                    // Avance sobre la ruta: lo que falta y una barra de progreso
                    Spacer(Modifier.height(Espacio.m))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (guia.llegada) "Ruta completada" else "Faltan ${formatoKm(guia.restanteKm)} km de la ruta",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${(guia.fraccion * 100).toInt()} %", style = MaterialTheme.typography.labelLarge, color = Runway.MoradoOscuro)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { guia.fraccion },
                        color = Runway.Morado,
                        trackColor = Runway.LavandaClaro,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                    )
                }
                Spacer(Modifier.height(Espacio.l))
                // Botones grandes y separados (especificación, sección 8)
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l)) {
                    if (pausado) {
                        BotonRunway("Reanudar", vm::reanudar, icono = Icons.Outlined.PlayArrow, alto = 64.dp, modifier = Modifier.weight(1f))
                    } else {
                        BotonRunway("Pausar", vm::pausar, estilo = EstiloBoton.Lima, icono = Icons.Outlined.Pause, alto = 64.dp, modifier = Modifier.weight(1f))
                    }
                    BotonRunway("Finalizar", { confirmarFin = true }, estilo = EstiloBoton.Oscuro, icono = Icons.Outlined.Flag, alto = 64.dp, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (confirmarFin) {
        AlertDialog(
            onDismissRequest = { confirmarFin = false },
            containerColor = Runway.Fondo,
            title = { Text("¿Finalizar entrenamiento?") },
            text = {
                Column {
                    Text("Se guardará en tu teléfono: ${formatoKm(s.distanciaM / 1000)} km en ${formatoCronometro(ui.duracionMs)}.")
                    TextButton(onClick = {
                        confirmarFin = false
                        confirmarDescarte = true
                    }) { Text("Descartar sin guardar", color = Runway.Error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarFin = false
                    terminando = true
                    vm.finalizar { id -> if (id != null) onFinalizado(id) else onSalir() }
                }) { Text("Finalizar y guardar", color = Runway.Morado) }
            },
            dismissButton = { TextButton(onClick = { confirmarFin = false }) { Text("Seguir", color = Runway.TextoSecundario) } },
        )
    }
    if (confirmarDescarte) {
        AlertDialog(
            onDismissRequest = { confirmarDescarte = false },
            containerColor = Runway.Fondo,
            title = { Text("¿Descartar entrenamiento?") },
            text = { Text("Se borrará el recorrido registrado. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarDescarte = false
                    vm.descartar(onSalir)
                }) { Text("Descartar", color = Runway.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmarDescarte = false }) { Text("Cancelar", color = Runway.TextoSecundario) } },
        )
    }
}

@Composable
private fun Metrica(etiqueta: String, valor: String, modifier: Modifier = Modifier, unidad: String = "") {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, style = MaterialTheme.typography.headlineMedium)
            if (unidad.isNotEmpty()) {
                Text(unidad, style = MaterialTheme.typography.labelSmall, color = Runway.TextoSecundario, modifier = Modifier.padding(bottom = 4.dp, start = 2.dp))
            }
        }
    }
}
