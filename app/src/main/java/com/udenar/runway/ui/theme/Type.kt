package com.udenar.runway.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.udenar.runway.R

// Plus Jakarta Sans (licencia OFL), fuente variable: un solo archivo para todos los pesos.
private fun jakarta(peso: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
)

val PlusJakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private fun estilo(tam: Int, peso: FontWeight, alto: Int, espaciado: Double = 0.0) = TextStyle(
    fontFamily = PlusJakarta,
    fontWeight = peso,
    fontSize = tam.sp,
    lineHeight = alto.sp,
    letterSpacing = espaciado.em,
)

val TipografiaRunway = Typography(
    // Métricas grandes: distancia, tiempo, ritmo
    displayLarge = estilo(56, FontWeight.Bold, 60, -0.02),
    displayMedium = estilo(40, FontWeight.Bold, 44, -0.02),
    displaySmall = estilo(30, FontWeight.Bold, 34, -0.01),
    // Títulos breves en semibold
    headlineMedium = estilo(26, FontWeight.SemiBold, 32, -0.01),
    headlineSmall = estilo(22, FontWeight.SemiBold, 28),
    titleLarge = estilo(20, FontWeight.SemiBold, 26),
    titleMedium = estilo(17, FontWeight.SemiBold, 22),
    titleSmall = estilo(15, FontWeight.SemiBold, 20),
    bodyLarge = estilo(16, FontWeight.Normal, 24),
    bodyMedium = estilo(14, FontWeight.Normal, 20),
    bodySmall = estilo(12, FontWeight.Normal, 16),
    labelLarge = estilo(15, FontWeight.SemiBold, 20),
    labelMedium = estilo(13, FontWeight.Medium, 18),
    labelSmall = estilo(11, FontWeight.Medium, 14),
)
