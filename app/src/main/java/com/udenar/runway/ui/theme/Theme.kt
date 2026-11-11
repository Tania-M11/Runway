package com.udenar.runway.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Escala única de espaciado y radios (sección 11: mantener la misma escala en todas las pantallas). */
object Espacio {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val margen = 20.dp          // margen lateral de las pantallas
    val radioTarjeta = 28.dp
    val radioPequeno = 16.dp
    val altoBoton = 56.dp
}

private val Colores = lightColorScheme(
    primary = Runway.Morado,
    onPrimary = Color.White,
    primaryContainer = Runway.LavandaClaro,
    onPrimaryContainer = Runway.MoradoOscuro,
    secondary = Runway.Lima,
    onSecondary = Runway.Texto,
    secondaryContainer = Runway.LimaClaro,
    onSecondaryContainer = Runway.Texto,
    background = Runway.Fondo,
    onBackground = Runway.Texto,
    surface = Runway.Superficie,
    onSurface = Runway.Texto,
    surfaceVariant = Runway.Fondo,
    onSurfaceVariant = Runway.TextoSecundario,
    surfaceContainer = Runway.Superficie,
    outline = Runway.Borde,
    outlineVariant = Runway.Borde,
    error = Runway.Error,
    onError = Color.White,
)

private val Formas = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(Espacio.radioPequeno),
    large = RoundedCornerShape(Espacio.radioTarjeta),
)

/** La referencia visual es clara; la app usa siempre el tema claro. */
@Composable
fun RunwayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colores,
        typography = TipografiaRunway,
        shapes = Formas,
        content = content,
    )
}
