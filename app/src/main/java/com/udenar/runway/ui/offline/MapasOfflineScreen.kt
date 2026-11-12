package com.udenar.runway.ui.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.RunwayApp
import com.udenar.runway.domain.ZonaOffline
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.historial.fechaCorta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import kotlinx.coroutines.launch

/** Zonas de mapa guardadas en el teléfono: tamaño, estado, reanudar y eliminar. */
@Composable
fun MapasOfflineScreen(onVolver: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as RunwayApp).contenedor.mapasOffline
    val zonas by repo.zonas.collectAsStateWithLifecycle()
    val alcance = rememberCoroutineScope()
    var porEliminar by remember { mutableStateOf<ZonaOffline?>(null) }
    LaunchedEffect(Unit) { repo.refrescar() }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Espacio.margen, end = Espacio.margen, bottom = Espacio.xl),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = onVolver)
                Spacer(Modifier.width(Espacio.m))
                EncabezadoPantalla("Mapas sin conexión", Modifier.weight(1f))
            }
        }
        item {
            Text(
                "El mapa de estas zonas está guardado en el teléfono: se ve completo aunque no tengas señal. " +
                    "Descárgalas desde el detalle de una ruta o con el botón de descarga en Explorar." +
                    if (zonas.isNotEmpty()) " Ocupan ${formatoMegas(zonas.sumOf { it.bytes })} en total." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Runway.TextoSecundario,
            )
        }
        if (zonas.isEmpty()) {
            item {
                EstadoVacio(
                    icono = Icons.Outlined.CloudOff,
                    titulo = "Aún no hay mapas descargados",
                    texto = "Antes de correr por una zona sin cobertura, descarga su mapa.",
                )
            }
        }
        items(zonas, key = { it.id }) { z ->
            TarjetaRunway(Modifier.fillMaxWidth(), relleno = Espacio.l) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(z.nombre, style = MaterialTheme.typography.titleSmall)
                        Text(
                            buildString {
                                if (z.creadaEn > 0) append(fechaCorta(z.creadaEn)).append(" · ")
                                append(formatoMegas(z.bytes))
                                if (!z.completa) append(" · incompleta")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Runway.TextoSecundario,
                        )
                    }
                    if (!z.completa) TextButton(onClick = { repo.reanudar(z.id) }) { Text("Reanudar", color = Runway.Morado) }
                    BotonCircular(Icons.Outlined.DeleteOutline, "Eliminar zona", onClick = { porEliminar = z })
                }
                if (!z.completa && z.recursosRequeridos > 0) {
                    Spacer(Modifier.height(Espacio.s))
                    LinearProgressIndicator(
                        progress = { (z.recursosCompletados.toFloat() / z.recursosRequeridos).coerceIn(0f, 1f) },
                        color = Runway.Morado,
                        trackColor = Runway.LavandaClaro,
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                }
            }
        }
    }

    porEliminar?.let { z ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            containerColor = Runway.Fondo,
            title = { Text("¿Eliminar este mapa?") },
            text = { Text("«${z.nombre}» dejará de verse sin conexión y se liberarán ${formatoMegas(z.bytes)}.") },
            confirmButton = {
                TextButton(onClick = {
                    porEliminar = null
                    alcance.launch { repo.eliminar(z.id) }
                }) { Text("Eliminar", color = Runway.Error) }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar", color = Runway.TextoSecundario) } },
        )
    }
}
