package com.udenar.runway.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Campo de búsqueda en forma de píldora blanca, para flotar sobre el mapa. */
@Composable
fun BarraBusqueda(
    texto: String,
    onTexto: (String) -> Unit,
    sugerencia: String,
    modifier: Modifier = Modifier,
    onLimpiar: () -> Unit = { onTexto("") },
) {
    val foco = LocalFocusManager.current
    Surface(
        shape = CircleShape,
        color = Runway.Superficie,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().height(Espacio.altoBoton),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 4.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Runway.TextoSecundario)
            Spacer(Modifier.width(Espacio.m))
            Box(Modifier.weight(1f)) {
                if (texto.isEmpty()) {
                    Text(sugerencia, style = MaterialTheme.typography.bodyLarge, color = Runway.TextoSecundario)
                }
                BasicTextField(
                    value = texto,
                    onValueChange = onTexto,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Runway.Texto),
                    cursorBrush = SolidColor(Runway.Morado),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { foco.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (texto.isNotEmpty()) {
                IconButton(onClick = {
                    onLimpiar()
                    foco.clearFocus()
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Borrar búsqueda", tint = Runway.TextoSecundario)
                }
            } else {
                Spacer(Modifier.width(Espacio.l))
            }
        }
    }
}
