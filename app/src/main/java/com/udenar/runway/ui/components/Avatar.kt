package com.udenar.runway.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udenar.runway.R
import com.udenar.runway.domain.AVATARES
import com.udenar.runway.ui.theme.Runway

/** Imagen de cada avatar (ilustraciones propias de RUNWAY). */
fun recursoAvatar(id: String): Int = when (id) {
    "avatar_2" -> R.drawable.avatar_2
    "avatar_3" -> R.drawable.avatar_3
    "avatar_4" -> R.drawable.avatar_4
    "avatar_5" -> R.drawable.avatar_5
    else -> R.drawable.avatar_1
}

@Composable
fun AvatarImagen(id: String, modifier: Modifier = Modifier, tamano: Dp = 48.dp) {
    Image(
        painter = painterResource(recursoAvatar(id)),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(tamano).clip(CircleShape),
    )
}

/** Selector de avatar: los 5 en fila; el elegido con anillo morado y ✓. */
@Composable
fun SelectorAvatar(seleccionado: String, onSeleccion: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AVATARES.forEachIndexed { i, id ->
            val activo = id == seleccionado
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    onClick = { onSeleccion(id) },
                    shape = CircleShape,
                    border = if (activo) BorderStroke(3.dp, Runway.Morado) else null,
                    color = Runway.Superficie,
                    modifier = Modifier.semantics {
                        selected = activo
                        role = Role.RadioButton
                        contentDescription = "Avatar ${i + 1}"
                    },
                ) {
                    AvatarImagen(id, Modifier.padding(if (activo) 3.dp else 0.dp), tamano = 56.dp)
                }
                if (activo) {
                    Surface(shape = CircleShape, color = Runway.Morado, modifier = Modifier.size(20.dp).border(2.dp, Runway.Superficie, CircleShape)) {
                        Icon(Icons.Rounded.Check, null, tint = Runway.Superficie, modifier = Modifier.padding(3.dp))
                    }
                }
            }
        }
    }
}
