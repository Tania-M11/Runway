package com.udenar.runway.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Campo de texto con el estilo de RUNWAY (bordes redondeados, foco morado). */
@Composable
fun CampoTexto(
    valor: String,
    onValor: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    ayuda: String? = null,
    onListo: () -> Unit = {},
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(etiqueta) },
        supportingText = ayuda?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(Espacio.radioPequeno),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onListo() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Runway.Morado,
            focusedLabelColor = Runway.Morado,
            cursorColor = Runway.Morado,
            unfocusedBorderColor = Runway.Borde,
            focusedContainerColor = Runway.Superficie,
            unfocusedContainerColor = Runway.Superficie,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Opción grande con título y descripción (selección única). La elegida se marca con
 * borde morado, fondo lavanda y un ✓, para no depender solo del color.
 */
@Composable
fun TarjetaOpcion(
    titulo: String,
    descripcion: String,
    seleccionada: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Espacio.radioPequeno + 4.dp),
        color = if (seleccionada) Runway.LavandaClaro else Runway.Superficie,
        border = BorderStroke(if (seleccionada) 2.dp else 1.dp, if (seleccionada) Runway.Morado else Runway.Borde),
        modifier = modifier.fillMaxWidth().semantics {
            selected = seleccionada
            role = Role.RadioButton
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Espacio.l)) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(descripcion, style = MaterialTheme.typography.bodyMedium, color = Runway.TextoSecundario)
            }
            Spacer(Modifier.width(Espacio.m))
            Surface(
                shape = CircleShape,
                color = if (seleccionada) Runway.Morado else Runway.Superficie,
                border = if (seleccionada) null else BorderStroke(1.5.dp, Runway.Borde),
                modifier = Modifier.size(26.dp),
            ) {
                if (seleccionada) Icon(Icons.Rounded.Check, null, tint = Runway.Superficie, modifier = Modifier.padding(4.dp))
            }
        }
    }
}
