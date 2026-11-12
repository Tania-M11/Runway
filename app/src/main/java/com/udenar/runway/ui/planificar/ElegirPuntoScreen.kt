package com.udenar.runway.ui.planificar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.domain.CAMARA_POR_DEFECTO
import com.udenar.runway.domain.LugarSugerido
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.mapa.MarcadorLugar
import com.udenar.runway.ui.mapa.Superposicion
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/**
 * Elegir un punto en el mapa (inicio o destino) con un pin fijo en el centro.
 *
 * Demuestra el controlador del mapa (MapController):
 * - **Estado inicial:** la cámara arranca encuadrando el inicio y el alcance de la distancia.
 * - **Marcadores:** el inicio (lima), el destino actual (bandera) y los lugares sugeridos.
 * - **Superposición:** un círculo con el radio ideal para la distancia pedida.
 * - **Eventos:** al detenerse la cámara se lee su centro; al tocar una sugerencia, se elige.
 */
@Composable
fun ElegirPuntoScreen(vm: PlanificadorViewModel, objetivo: ObjetivoPunto, onListo: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(objetivo) { if (e.selector?.objetivo != objetivo) vm.abrirSelector(objetivo) }
    val s = e.selector
    val esDestino = objetivo == ObjetivoPunto.Destino
    val inicio = e.inicio?.coordenada
    val camara = remember {
        val c = (if (esDestino) e.destino?.coordenada else e.inicio?.coordenada) ?: inicio
        if (c != null) PosicionCamara(c, 15.0) else CAMARA_POR_DEFECTO
    }

    Box(Modifier.fillMaxSize()) {
        MapaRunway(
            camaraInicial = camara,
            puntoSeleccionado = inicio.takeIf { esDestino },
            destinoRuta = e.destino?.coordenada.takeIf { !esDestino },
            lugares = if (esDestino) e.sugerencias.map { MarcadorLugar(idSugerencia(it), it.lugar.nombre, it.lugar.coordenada) } else emptyList(),
            superposiciones = if (esDestino && inicio != null) listOf(Superposicion.Circulo(inicio, e.radioIdealKm)) else emptyList(),
            comandos = vm.comandosMapa,
            onEstiloCargado = vm::encuadrarSelector,
            onCamaraQuieta = { vm.onCentroSelector(it.centro) },
            onToqueLugar = if (esDestino) { id ->
                vm.confirmarSelector(sugeridoId = id)
                onListo()
            } else null,
            modifier = Modifier.fillMaxSize(),
        )

        // Pin fijo en el centro: la punta marca el punto que se elegirá
        Icon(
            Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = if (esDestino) Runway.Texto else Runway.MoradoOscuro,
            modifier = Modifier.align(Alignment.Center).size(52.dp).offset(y = (-24).dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.statusBarsPadding().padding(Espacio.l),
        ) {
            BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = {
                vm.cerrarSelector()
                onListo()
            }, sombra = 4.dp)
            Spacer(Modifier.width(Espacio.s))
            Surface(shape = CircleShape, color = Runway.Superficie, shadowElevation = 4.dp) {
                Text(
                    if (esDestino) "Elige a dónde llegar" else "Elige dónde empezar",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = Espacio.l, vertical = Espacio.m),
                )
            }
        }

        // Panel inferior: punto actual, sugerencias y confirmar
        Surface(
            shape = RoundedCornerShape(topStart = Espacio.radioTarjeta, topEnd = Espacio.radioTarjeta),
            color = Runway.Superficie,
            shadowElevation = 12.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Column(Modifier.navigationBarsPadding().padding(Espacio.margen), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
                Text("Mueve el mapa para ubicar el pin", style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
                Text(
                    s?.nombre ?: "Buscando dirección…",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (esDestino && inicio != null) {
                    Text(
                        "El círculo marca unos ${formatoKm(e.radioIdealKm)} km en línea recta: lo ideal para tus ${formatoKm(e.distanciaKm)} km " +
                            if (e.modalidad.name == "Circular") "de ida y vuelta." else "hasta el destino.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Runway.TextoSecundario,
                    )
                    if (e.sugerencias.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                            items(e.sugerencias, key = { idSugerencia(it) }) { sug ->
                                ChipSugerencia(sug) {
                                    vm.confirmarSelector(sugeridoId = idSugerencia(sug))
                                    onListo()
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Espacio.xs))
                BotonRunway(
                    if (esDestino) "Llegar hasta aquí" else "Empezar desde aquí",
                    onClick = {
                        vm.confirmarSelector()
                        onListo()
                    },
                    icono = Icons.Outlined.Check,
                    habilitado = s?.centro != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Lugar sugerido: tipo, nombre y distancia en línea recta desde el inicio. */
@Composable
fun ChipSugerencia(s: LugarSugerido, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(Espacio.radioPequeno), color = Runway.LavandaClaro) {
        Column(Modifier.padding(horizontal = Espacio.m, vertical = Espacio.s).width(150.dp)) {
            Text(s.tipo, style = MaterialTheme.typography.labelSmall, color = Runway.MoradoOscuro)
            Text(s.lugar.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${formatoKm(s.distanciaRectaKm)} km en línea recta", style = MaterialTheme.typography.labelSmall, color = Runway.TextoSecundario)
        }
    }
}
