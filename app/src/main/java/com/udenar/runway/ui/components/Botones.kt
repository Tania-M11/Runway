package com.udenar.runway.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/**
 * Pesos visuales de botón. Una sola acción [Principal] por pantalla (sección 3.1);
 * las demás usan [Suave] o [Contorno].
 */
enum class EstiloBoton { Principal, Lima, Oscuro, Suave, Contorno, Blanco }

private data class ColoresBoton(val fondo: Color, val texto: Color, val borde: Color? = null)

private fun colores(estilo: EstiloBoton) = when (estilo) {
    EstiloBoton.Principal -> ColoresBoton(Runway.Morado, Color.White)
    EstiloBoton.Lima -> ColoresBoton(Runway.Lima, Runway.Texto)
    EstiloBoton.Oscuro -> ColoresBoton(Runway.Texto, Color.White)
    EstiloBoton.Suave -> ColoresBoton(Runway.LavandaClaro, Runway.MoradoOscuro)
    EstiloBoton.Contorno -> ColoresBoton(Color.Transparent, Runway.Texto, Runway.Borde)
    EstiloBoton.Blanco -> ColoresBoton(Color.White, Runway.Texto)
}

/** Botón en forma de píldora, alto fijo de 56 dp. */
@Composable
fun BotonRunway(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    estilo: EstiloBoton = EstiloBoton.Principal,
    icono: ImageVector? = null,
    habilitado: Boolean = true,
    cargando: Boolean = false,
    alto: Dp = Espacio.altoBoton,
) {
    val c = colores(estilo)
    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = c.fondo,
            contentColor = c.texto,
            disabledContainerColor = c.fondo.copy(alpha = if (c.fondo == Color.Transparent) 0f else 0.45f),
            disabledContentColor = c.texto.copy(alpha = 0.6f),
        ),
        border = c.borde?.let { androidx.compose.foundation.BorderStroke(1.dp, it) },
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.height(alto),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (cargando) {
                CircularProgressIndicator(color = c.texto, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Espacio.s))
            } else if (icono != null) {
                Icon(icono, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Espacio.s))
            }
            Text(texto, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Botón redondo con ícono (atrás, buscar, centrar en mí…). */
@Composable
fun BotonCircular(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fondo: Color = Color.White,
    colorIcono: Color = Runway.Texto,
    tamano: Dp = 48.dp,
    sombra: Dp = 0.dp,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = fondo,
        contentColor = colorIcono,
        shadowElevation = sombra,
        modifier = modifier.size(tamano),
    ) {
        Icon(icono, contentDescription = descripcion, modifier = Modifier.padding(tamano * 0.25f))
    }
}
