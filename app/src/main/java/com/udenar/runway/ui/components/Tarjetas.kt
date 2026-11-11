package com.udenar.runway.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Tarjeta base: superficie blanca (o pastel) con esquinas de 28 dp. */
@Composable
fun TarjetaRunway(
    modifier: Modifier = Modifier,
    color: Color = Runway.Superficie,
    onClick: (() -> Unit)? = null,
    relleno: Dp = Espacio.margen,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val forma = RoundedCornerShape(Espacio.radioTarjeta)
    val cuerpo: @Composable () -> Unit = {
        Column(Modifier.padding(relleno), content = contenido)
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = forma, color = color, modifier = modifier, content = cuerpo)
    } else {
        Surface(shape = forma, color = color, modifier = modifier, content = cuerpo)
    }
}

/** Tarjeta morada protagonista, como "Daily Challenge" de la referencia. */
@Composable
fun TarjetaDestacada(
    titulo: String,
    texto: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    acciones: @Composable ColumnScope.() -> Unit = {},
) {
    TarjetaRunway(modifier = modifier, color = Runway.Morado) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(40.dp)) {
                Icon(icono, null, tint = Runway.Lima, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(Espacio.m))
            Text(titulo, style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        Spacer(Modifier.height(Espacio.s))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(Espacio.l))
        acciones()
    }
}

/**
 * Tarjeta de métrica con etiqueta, número grande y unidad
 * (jerarquía clara para distancia, tiempo y ritmo).
 */
@Composable
fun TarjetaMetrica(
    etiqueta: String,
    valor: String,
    unidad: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    fondo: Color = Runway.Superficie,
    acento: Color = Runway.Morado,
) {
    TarjetaRunway(modifier = modifier, color = fondo, relleno = Espacio.l) {
        // Ícono arriba y etiqueta en su propia línea: cabe completa aunque haya 3 tarjetas por fila
        Surface(shape = CircleShape, color = acento.copy(alpha = 0.16f), modifier = Modifier.size(32.dp)) {
            Icon(icono, null, tint = acento, modifier = Modifier.padding(7.dp))
        }
        Spacer(Modifier.height(Espacio.s))
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = Runway.TextoSecundario,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Espacio.xs))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(valor, style = MaterialTheme.typography.displaySmall, color = Runway.Texto)
            Text(
                unidad,
                style = MaterialTheme.typography.labelMedium,
                color = Runway.TextoSecundario,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
    }
}

/** Fila de acceso (ícono + título + descripción + flecha), p. ej. "Mis rutas". */
@Composable
fun TarjetaAcceso(
    titulo: String,
    descripcion: String,
    icono: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fondoIcono: Color = Runway.LimaClaro,
) {
    TarjetaRunway(modifier = modifier.fillMaxWidth(), onClick = onClick, relleno = Espacio.l) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Espacio.radioPequeno), color = fondoIcono, modifier = Modifier.size(48.dp)) {
                Icon(icono, null, tint = Runway.Texto, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.width(Espacio.l))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                Text(descripcion, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario)
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = Runway.TextoSecundario,
            )
        }
    }
}
