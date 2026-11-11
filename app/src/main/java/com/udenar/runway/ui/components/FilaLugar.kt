package com.udenar.runway.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udenar.runway.domain.Lugar
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Fila de un resultado del buscador de lugares. */
@Composable
fun FilaLugar(lugar: Lugar, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Espacio.l, vertical = Espacio.m),
    ) {
        Icon(Icons.Outlined.Place, null, tint = Runway.Morado, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Espacio.l))
        Column {
            Text(lugar.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (lugar.detalle.isNotBlank()) {
                Text(
                    lugar.detalle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Runway.TextoSecundario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
