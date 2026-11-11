package com.udenar.runway.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/**
 * Estado vacío: ilustración (o ícono mientras llegan las ilustraciones),
 * explicación corta y una acción útil para comenzar (sección 7, "Lista vacía").
 */
@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    texto: String,
    modifier: Modifier = Modifier,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Espacio.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = Runway.LavandaClaro, modifier = Modifier.size(88.dp)) {
            Icon(icono, null, tint = Runway.Morado, modifier = Modifier.padding(24.dp))
        }
        Spacer(Modifier.height(Espacio.l))
        Text(titulo, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Espacio.xs))
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            color = Runway.TextoSecundario,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Espacio.l),
        )
        if (accion != null && onAccion != null) {
            Spacer(Modifier.height(Espacio.l))
            BotonRunway(accion, onAccion, estilo = EstiloBoton.Suave)
        }
    }
}

/** Situaciones de la sección 7 que se muestran como aviso discreto. */
enum class TipoAviso(val icono: ImageVector, val fondo: Color, val color: Color) {
    SinConexion(Icons.Outlined.CloudOff, Runway.Fondo, Runway.TextoSecundario),
    PendienteSincronizar(Icons.Outlined.CloudSync, Runway.LavandaClaro, Runway.MoradoOscuro),
    Sincronizado(Icons.Outlined.CheckCircle, Runway.LimaClaro, Runway.Texto),
    GpsDebil(Icons.Outlined.GpsNotFixed, Runway.AvisoClaro, Runway.Aviso),
    Error(Icons.Outlined.ErrorOutline, Runway.ErrorClaro, Runway.Error),
}

/** Aviso en forma de píldora: ícono + texto (+ acción opcional, p. ej. "Reintentar"). */
@Composable
fun AvisoEstado(
    tipo: TipoAviso,
    texto: String,
    modifier: Modifier = Modifier,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    Surface(shape = CircleShape, color = tipo.fondo, contentColor = tipo.color, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = if (accion != null) 4.dp else 16.dp),
        ) {
            Icon(tipo.icono, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Espacio.s))
            Text(
                texto,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 10.dp).weight(1f, fill = false),
            )
            if (accion != null && onAccion != null) {
                TextButton(onClick = onAccion) {
                    Text(accion, style = MaterialTheme.typography.labelLarge, color = tipo.color)
                }
            }
        }
    }
}

/** Carga dentro del área de resultados con texto breve ("Buscando recorridos…"). */
@Composable
fun EstadoCargando(texto: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Espacio.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = Runway.Morado, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Espacio.m))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Runway.TextoSecundario)
    }
}
