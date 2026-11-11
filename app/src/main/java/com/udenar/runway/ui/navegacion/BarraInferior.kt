package com.udenar.runway.ui.navegacion

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Runway

/**
 * Barra inferior fija: ícono + etiqueta, la sección activa en morado y
 * "Entrenar" destacado en el centro (especificación, sección 4).
 */
@Composable
fun BarraInferior(actual: Seccion?, onSeleccion: (Seccion) -> Unit) {
    Surface(
        color = Color.White,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Bottom,
        ) {
            Seccion.entries.forEach { s ->
                ItemBarra(
                    seccion = s,
                    activa = s == actual,
                    destacada = s == Seccion.Entrenar,
                    onClick = { onSeleccion(s) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ItemBarra(
    seccion: Seccion,
    activa: Boolean,
    destacada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorTexto by animateColorAsState(if (activa) Runway.Morado else Runway.TextoSecundario, label = "texto")
    Column(
        modifier = modifier.selectable(selected = activa, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (destacada) {
            // Acceso para iniciar un entrenamiento con presencia visual destacada
            Surface(shape = CircleShape, color = if (activa) Runway.MoradoOscuro else Runway.Morado, modifier = Modifier.size(52.dp)) {
                Icon(seccion.icono, null, tint = Runway.Lima, modifier = Modifier.padding(13.dp))
            }
        } else {
            val fondo by animateColorAsState(if (activa) Runway.LavandaClaro else Color.Transparent, label = "fondo")
            Surface(shape = CircleShape, color = fondo, modifier = Modifier.width(56.dp).height(32.dp)) {
                Icon(seccion.icono, null, tint = colorTexto, modifier = Modifier.padding(5.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(seccion.etiqueta, style = MaterialTheme.typography.labelSmall, color = colorTexto, maxLines = 1)
    }
}
