package com.udenar.runway.ui.offline

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.offline.RepositorioMapasOffline
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Teselas
import com.udenar.runway.domain.ZonaOffline
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.mapa.Superposicion
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import java.util.Locale

fun formatoMegas(bytes: Long): String = String.format(Locale.forLanguageTag("es-CO"), "%.1f MB", bytes / 1_048_576.0)

/** Zonas descargadas como superposición (rectángulos) para dibujarlas en el mapa. */
fun List<ZonaOffline>.comoSuperposiciones(): List<Superposicion> =
    filter { it.completa }.map { Superposicion.Rectangulo(it.suroeste, it.noreste) }

/**
 * "Mapa sin conexión" para el área de una ruta (especificación: no bloquear el uso del mapa
 * por falta de internet). Antes de descargar muestra cuántas teselas y MB aproximados son.
 */
@Composable
fun TarjetaMapaOffline(puntos: List<Coordenada>, nombre: String, modifier: Modifier = Modifier) {
    if (puntos.isEmpty()) return
    val repo = (LocalContext.current.applicationContext as RunwayApp).contenedor.mapasOffline
    val zonas by repo.zonas.collectAsStateWithLifecycle()
    val descargas by repo.descargas.collectAsStateWithLifecycle()
    val (sw, ne) = remember(puntos) { Teselas.limites(puntos, margenKm = 0.4) }
    val clave = remember(sw, ne) { repo.clave(sw, ne) }
    val progreso = descargas[clave]
    val cubierta = zonas.firstOrNull { it.completa && it.cubre(sw, ne) }
    val teselas = remember(sw, ne) { Teselas.contar(sw, ne) }
    var aviso by remember { mutableStateOf<String?>(null) }

    TarjetaRunway(modifier.fillMaxWidth(), color = if (cubierta != null) Runway.LimaClaro else Runway.Superficie, relleno = Espacio.l) {
        when {
            cubierta != null -> {
                Fila(Icons.Outlined.CloudDone, "Disponible sin conexión", "Mapa de esta zona guardado · ${formatoMegas(cubierta.bytes)}")
            }
            progreso != null && !progreso.terminada -> {
                Fila(
                    if (progreso.error != null) Icons.Outlined.ErrorOutline else Icons.Outlined.CloudDownload,
                    "Descargando mapa… ${(progreso.fraccion * 100).toInt()} %",
                    if (progreso.error != null) "Sin conexión: se reanudará sola cuando vuelva la red"
                    else "${progreso.completados} de ${progreso.requeridos} archivos · ${formatoMegas(progreso.bytes)}",
                )
                Spacer(Modifier.height(Espacio.s))
                LinearProgressIndicator(
                    progress = { progreso.fraccion },
                    color = Runway.Morado,
                    trackColor = Runway.LavandaClaro,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
            }
            else -> {
                Fila(
                    Icons.Outlined.CloudDownload,
                    "Mapa sin conexión",
                    "Guarda el mapa de esta zona para verlo sin señal (≈ ${String.format(Locale.US, "%.0f", Teselas.megasEstimados(teselas))} MB)",
                )
                Spacer(Modifier.height(Espacio.m))
                BotonRunway(
                    "Descargar mapa de esta zona",
                    onClick = {
                        aviso = when (val r = repo.descargar(nombre, sw, ne)) {
                            is RepositorioMapasOffline.Resultado.MuyGrande -> "La zona es muy grande (${r.teselas} teselas)."
                            RepositorioMapasOffline.Resultado.Iniciada -> null
                        }
                    },
                    estilo = EstiloBoton.Suave,
                    alto = 48.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Runway.Error) }
            }
        }
    }
}

@Composable
private fun Fila(icono: androidx.compose.ui.graphics.vector.ImageVector, titulo: String, detalle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = Runway.MoradoOscuro, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(Espacio.m))
        androidx.compose.foundation.layout.Column {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(detalle, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario)
        }
    }
}
