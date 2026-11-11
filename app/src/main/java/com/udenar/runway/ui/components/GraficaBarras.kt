package com.udenar.runway.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.Runway
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

/**
 * Gráfica de barras de una sola serie (Canvas). Siguiendo la guía de visualización:
 * - Un solo color (morado) y un solo eje; el título de la tarjeta nombra la serie.
 * - Barras con 4 dp redondeados solo arriba, ancladas a la línea base y separadas.
 * - Cuadrícula tenue: solo la línea base y una línea punteada en el máximo redondeado.
 * - El valor aparece solo en la barra tocada (no un número en cada barra).
 * - Las etiquetas del eje se muestran solo donde [mostrarEtiqueta] lo indica, para no amontonarlas.
 */
@Composable
fun GraficaBarras(
    valores: List<Double>,
    etiquetas: List<String>,
    formatoValor: (Double) -> String,
    descripcion: String,
    modifier: Modifier = Modifier,
    mostrarEtiqueta: (Int) -> Boolean = { true },
) {
    val medidor = rememberTextMeasurer()
    val estiloEje = MaterialTheme.typography.labelSmall.copy(color = Runway.TextoSecundario)
    val estiloValor = MaterialTheme.typography.labelMedium.copy(color = Runway.Texto)
    // Por defecto se resalta la última barra con datos (lo más reciente)
    var seleccion by remember(valores) { mutableStateOf(valores.indexOfLast { it > 0 }.takeIf { it >= 0 }) }
    val maximo = escalaRedonda(valores.maxOrNull() ?: 0.0)

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .semantics { contentDescription = descripcion }
                .pointerInput(valores) {
                    detectTapGestures { p ->
                        val i = (p.x / (size.width / valores.size)).toInt().coerceIn(0, valores.lastIndex)
                        seleccion = if (valores[i] > 0) i else null
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                val altoEtiqueta = 18.dp.toPx()
                val altoValor = 22.dp.toPx()
                val base = size.height - altoEtiqueta
                val areaAlto = base - altoValor
                val ranura = size.width / valores.size
                // Separación: amplia con pocas barras, mínima de 2 dp con muchas
                val hueco = maxOf(2.dp.toPx(), ranura * if (valores.size <= 12) 0.35f else 0.2f)
                val ancho = (ranura - hueco).coerceAtLeast(1f)
                val radio = minOf(4.dp.toPx(), ancho / 2)

                // Línea punteada del máximo y línea base
                if (maximo > 0) {
                    val yMax = base - areaAlto
                    drawLine(Runway.Borde, Offset(0f, yMax), Offset(size.width, yMax), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    texto(medidor, formatoValor(maximo), estiloEje, Offset(0f, yMax - 16.dp.toPx()))
                }
                drawLine(Runway.Borde, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())

                valores.forEachIndexed { i, v ->
                    val x = i * ranura + hueco / 2
                    if (v > 0 && maximo > 0) {
                        val alto = (v / maximo * areaAlto).toFloat().coerceAtLeast(3.dp.toPx())
                        val color = if (i == seleccion) Runway.MoradoOscuro else Runway.Morado
                        val barra = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = x, top = base - alto, right = x + ancho, bottom = base,
                                    topLeftCornerRadius = CornerRadius(radio), topRightCornerRadius = CornerRadius(radio),
                                ),
                            )
                        }
                        drawPath(barra, color)
                        if (i == seleccion) {
                            val etiqueta = formatoValor(v)
                            val medida = medidor.measure(etiqueta, estiloValor)
                            val xTexto = (x + ancho / 2 - medida.size.width / 2).coerceIn(0f, size.width - medida.size.width)
                            drawText(medida, topLeft = Offset(xTexto, base - alto - medida.size.height - 2.dp.toPx()))
                        }
                    }
                    if (mostrarEtiqueta(i)) {
                        val medida = medidor.measure(etiquetas[i], estiloEje)
                        drawText(medida, topLeft = Offset(x + ancho / 2 - medida.size.width / 2, base + 3.dp.toPx()))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.texto(
    medidor: TextMeasurer,
    texto: String,
    estilo: TextStyle,
    posicion: Offset,
) {
    drawText(medidor.measure(texto, estilo), topLeft = Offset(posicion.x, posicion.y.coerceAtLeast(0f)))
}

/** Redondea hacia arriba a 1, 2 o 5 × 10ⁿ para que la línea de referencia tenga un número limpio. */
fun escalaRedonda(v: Double): Double {
    if (v <= 0) return 0.0
    val magnitud = 10.0.pow(kotlin.math.floor(log10(v)))
    val n = v / magnitud
    val paso = when {
        n <= 1 -> 1.0
        n <= 2 -> 2.0
        n <= 5 -> 5.0
        else -> 10.0
    }
    return ceil(paso) * magnitud
}
