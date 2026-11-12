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
import com.udenar.runway.domain.formatoHoras
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.ui.components.AvatarImagen
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.historial.TarjetaEntrenamiento
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
    avatar: String = "avatar_1",
    entrenamientoEnCurso: Boolean = false,
    onVolverEntrenamiento: () -> Unit = {},
    datos: DatosInicio = DatosInicio(),
    onHistorial: () -> Unit = {},
    onAbrirEntrenamiento: (String) -> Unit = {},
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
            AvatarImagen(avatar, tamano = 52.dp)
            Spacer(Modifier.width(Espacio.m))
            Column {
                Text("Hoy es un buen día para correr", style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
                Text("Hola, $nombre", style = MaterialTheme.typography.titleLarge)
            }
        }

        // Acción principal (si hay un entrenamiento abierto, lo primero es volver a él)
        if (entrenamientoEnCurso) {
            TarjetaDestacada(
                titulo = "Tienes un entrenamiento en curso",
                texto = "Sigue registrando o finalízalo para ver tu resumen.",
                icono = Icons.AutoMirrored.Outlined.DirectionsRun,
            ) {
                BotonRunway("Volver al entrenamiento", onVolverEntrenamiento, estilo = EstiloBoton.Lima, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Espacio.l))
        }
        TarjetaDestacada(
            titulo = "¿Listo para correr?",
            texto = "Elige cuántos kilómetros quieres hacer y te proponemos recorridos donde estés.",
            icono = Icons.Outlined.Explore,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                BotonRunway("Planificar ruta", onPlanificar, estilo = EstiloBoton.Lima, modifier = Modifier.weight(1f))
                BotonRunway("Correr ya", onEmpezar, estilo = EstiloBoton.Blanco)
            }
        }

        // Resumen de actividad
        // Resumen de actividad acumulada (datos locales: funciona sin internet)
        EncabezadoSeccion("Tu actividad", accion = "Ver historial", onAccion = onHistorial)
        val tiempo = formatoHoras(datos.duracionMs)
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            TarjetaMetrica(
                "Distancia", formatoKm(datos.distanciaKm), "km", Icons.Outlined.Straighten,
                fondo = Runway.LimaClaro, acento = Runway.MoradoOscuro, modifier = Modifier.weight(1f),
            )
            TarjetaMetrica(
                "Salidas", datos.salidas.toString(), "", Icons.Outlined.Flag,
                fondo = Runway.CelesteClaro, acento = Runway.Celeste, modifier = Modifier.weight(1f),
            )
            TarjetaMetrica(
                "Tiempo", tiempo.substringBeforeLast(" "), tiempo.substringAfterLast(" "), Icons.Outlined.Schedule,
                fondo = Runway.RosaClaro, acento = Runway.Rosa, modifier = Modifier.weight(1f),
            )
        }
        if (datos.pendientes > 0) {
            // Indicador discreto de entrenamientos aún no sincronizados (especificación 5.2)
            Spacer(Modifier.height(Espacio.s))
            AvisoEstado(
                TipoAviso.PendienteSincronizar,
                if (datos.pendientes == 1) "1 entrenamiento pendiente de sincronizar"
                else "${datos.pendientes} entrenamientos pendientes de sincronizar",
            )
        }

        // Último entrenamiento
        EncabezadoSeccion("Último entrenamiento")
        val ultimo = datos.ultimo
        if (ultimo != null) {
            TarjetaEntrenamiento(ultimo, onClick = { onAbrirEntrenamiento(ultimo.id) })
        } else {
            TarjetaRunway(modifier = Modifier.fillMaxWidth()) {
                EstadoVacio(
                    icono = Icons.AutoMirrored.Outlined.DirectionsRun,
                    titulo = "Tu primera carrera te espera",
                    texto = "Cuando termines un entrenamiento verás aquí tu distancia, tiempo y ritmo.",
                    accion = "Empezar a correr",
                    onAccion = onEmpezar,
                )
            }
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
