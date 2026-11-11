package com.udenar.runway.ui.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EncabezadoSeccion
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaAcceso
import com.udenar.runway.ui.components.TarjetaDestacada
import com.udenar.runway.ui.components.TarjetaMetrica
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import com.udenar.runway.ui.theme.RunwayTheme

/**
 * Inicio (especificación 5.2). Por ahora muestra el estado real de una instalación nueva:
 * sin entrenamientos. Las métricas se conectan a Room en la parte del historial.
 */
@Composable
fun InicioScreen(
    onPlanificar: () -> Unit,
    onEmpezar: () -> Unit,
    onMisRutas: () -> Unit,
    nombre: String = "corredor",
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Espacio.margen)
            .padding(bottom = Espacio.xl),
    ) {
        // Saludo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Espacio.l, bottom = Espacio.xl),
        ) {
            Surface(shape = CircleShape, color = Runway.Lima, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(nombre.first().uppercase(), style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(Espacio.m))
            Column {
                Text("Hoy es un buen día para correr", style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
                Text("Hola, $nombre", style = MaterialTheme.typography.titleLarge)
            }
        }

        // Acción principal
        TarjetaDestacada(
            titulo = "¿Listo para correr?",
            texto = "Elige cuántos kilómetros quieres hacer y te proponemos recorridos por Ipiales.",
            icono = Icons.Outlined.Explore,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                BotonRunway("Planificar ruta", onPlanificar, estilo = EstiloBoton.Lima, modifier = Modifier.weight(1f))
                BotonRunway("Correr ya", onEmpezar, estilo = EstiloBoton.Blanco)
            }
        }

        // Resumen de actividad
        EncabezadoSeccion("Tu actividad")
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            TarjetaMetrica(
                "Distancia", "0", "km", Icons.Outlined.Straighten,
                fondo = Runway.LimaClaro, acento = Runway.MoradoOscuro, modifier = Modifier.weight(1f),
            )
            TarjetaMetrica(
                "Salidas", "0", "", Icons.Outlined.Flag,
                fondo = Runway.CelesteClaro, acento = Runway.Celeste, modifier = Modifier.weight(1f),
            )
            TarjetaMetrica(
                "Tiempo", "0", "min", Icons.Outlined.Schedule,
                fondo = Runway.RosaClaro, acento = Runway.Rosa, modifier = Modifier.weight(1f),
            )
        }

        // Último entrenamiento (estado vacío motivador)
        EncabezadoSeccion("Último entrenamiento")
        TarjetaRunway(modifier = Modifier.fillMaxWidth()) {
            EstadoVacio(
                icono = Icons.AutoMirrored.Outlined.DirectionsRun,
                titulo = "Tu primera carrera te espera",
                texto = "Cuando termines un entrenamiento verás aquí tu distancia, tiempo y ritmo.",
                accion = "Empezar a correr",
                onAccion = onEmpezar,
            )
        }

        Spacer(Modifier.height(Espacio.l))
        TarjetaAcceso(
            titulo = "Mis rutas",
            descripcion = "Tus recorridos privados y reutilizables",
            icono = Icons.Outlined.Bookmarks,
            onClick = onMisRutas,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6FA, heightDp = 1100)
@Composable
private fun InicioPreview() {
    RunwayTheme { InicioScreen({}, {}, {}) }
}
