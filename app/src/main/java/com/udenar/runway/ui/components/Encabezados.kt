package com.udenar.runway.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Título de bloque con acción secundaria a la derecha ("Ver todo >"). */
@Composable
fun EncabezadoSeccion(
    titulo: String,
    modifier: Modifier = Modifier,
    accion: String? = null,
    onAccion: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Espacio.xl, bottom = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(titulo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (accion != null && onAccion != null) {
            TextButton(onClick = onAccion) {
                Text(accion, style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null,
                    tint = Runway.TextoSecundario,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Encabezado de pantalla: título grande, subtítulo opcional y acciones a la derecha. */
@Composable
fun EncabezadoPantalla(
    titulo: String,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    acciones: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Espacio.l, bottom = Espacio.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitulo != null) {
                Text(subtitulo, style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
            }
            Text(titulo, style = MaterialTheme.typography.headlineMedium)
        }
        acciones()
    }
}
