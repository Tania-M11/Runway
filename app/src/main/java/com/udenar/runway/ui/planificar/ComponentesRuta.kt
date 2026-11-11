package com.udenar.runway.ui.planificar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udenar.runway.domain.Exigencia
import com.udenar.runway.domain.RutaCandidata
import com.udenar.runway.domain.formatoDuracion
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.domain.formatoMetros
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Colores diferenciados para comparar alternativas en el mapa (especificación 5.5). */
val COLORES_RUTAS = listOf("#7668E8", "#2E9BD6", "#E5679C", "#E8892E")

fun colorDeRuta(indice: Int): String = COLORES_RUTAS[indice % COLORES_RUTAS.size]

fun String.aColor(): Color = Color(android.graphics.Color.parseColor(this))

/** Etiqueta de exigencia con ícono de barras + texto (no depende solo del color). */
@Composable
fun EtiquetaExigencia(exigencia: Exigencia) {
    val (fondo, texto) = when (exigencia) {
        Exigencia.Suave -> Runway.LimaClaro to Runway.Texto
        Exigencia.Moderada -> Runway.AvisoClaro to Runway.Aviso
        Exigencia.Exigente -> Runway.RosaClaro to Runway.Rosa
    }
    Surface(shape = CircleShape, color = fondo, contentColor = texto) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            // 1, 2 o 3 barras según la exigencia
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                repeat(3) { i ->
                    Surface(
                        shape = RoundedCornerShape(1.dp),
                        color = if (i <= exigencia.ordinal) texto else texto.copy(alpha = 0.25f),
                        modifier = Modifier.width(3.dp).height((5 + i * 3).dp),
                    ) {}
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(exigencia.etiqueta, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun DatoRuta(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = Runway.TextoSecundario, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(texto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Tarjeta de una alternativa en la lista de resultados. */
@Composable
fun TarjetaAlternativa(
    ruta: RutaCandidata,
    colorHex: String,
    recomendada: Boolean,
    seleccionada: Boolean,
    onClick: () -> Unit,
    accion: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Espacio.radioPequeno + 8.dp),
        color = Runway.Superficie,
        border = BorderStroke(if (seleccionada) 2.dp else 1.dp, if (seleccionada) colorHex.aColor() else Runway.Borde),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Espacio.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = colorHex.aColor(), modifier = Modifier.size(14.dp)) {}
                Spacer(Modifier.width(Espacio.s))
                Text(
                    ruta.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (recomendada) {
                    Surface(shape = CircleShape, color = Runway.Lima) {
                        Text("Recomendada", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Spacer(Modifier.height(Espacio.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.l), verticalAlignment = Alignment.CenterVertically) {
                DatoRuta(Icons.Outlined.Straighten, "${formatoKm(ruta.distanciaKm)} km")
                DatoRuta(Icons.AutoMirrored.Outlined.TrendingUp, "${formatoMetros(ruta.subidaM)} m")
                DatoRuta(Icons.Outlined.Schedule, "~${formatoDuracion(ruta.tiempoMin)}")
            }
            Spacer(Modifier.height(Espacio.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EtiquetaExigencia(ruta.exigencia)
                if (ruta.calles.isNotEmpty()) {
                    Spacer(Modifier.width(Espacio.s))
                    Text(
                        "Por ${ruta.calles.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Runway.TextoSecundario,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (seleccionada) {
                Spacer(Modifier.height(Espacio.m))
                accion()
            }
        }
    }
}

/** Perfil de elevación simple dibujado con Canvas (altitud a lo largo del recorrido). */
@Composable
fun PerfilElevacion(elevaciones: List<Double>, color: Color, modifier: Modifier = Modifier) {
    if (elevaciones.size < 2) return
    val minimo = elevaciones.min()
    val maximo = elevaciones.max()
    val rango = (maximo - minimo).coerceAtLeast(10.0) // evita exagerar rutas casi planas
    Column(modifier) {
        Row {
            Text("${formatoMetros(maximo)} m", style = MaterialTheme.typography.labelSmall, color = Runway.TextoSecundario)
            Spacer(Modifier.weight(1f))
            Text("Altitud", style = MaterialTheme.typography.labelSmall, color = Runway.TextoSecundario)
        }
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 6.dp)) {
            val paso = size.width / (elevaciones.size - 1)
            fun y(e: Double) = (size.height - ((e - minimo) / rango * size.height)).toFloat()
            val linea = Path().apply {
                moveTo(0f, y(elevaciones.first()))
                elevaciones.forEachIndexed { i, e -> if (i > 0) lineTo(i * paso, y(e)) }
            }
            val relleno = Path().apply {
                addPath(linea)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(relleno, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
            drawPath(linea, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(Runway.Borde, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }
        Text("${formatoMetros(minimo)} m", style = MaterialTheme.typography.labelSmall, color = Runway.TextoSecundario)
    }
}
