package com.udenar.runway.ui.theme

import androidx.compose.ui.graphics.Color

/** Paleta de la especificación visual de RUNWAY (sección 3). */
object Runway {
    val Morado = Color(0xFF7668E8)        // acciones principales e identidad
    val MoradoOscuro = Color(0xFF5144B8)  // textos o elementos de mayor contraste
    val Lima = Color(0xFFD5F36A)          // progreso, objetivos cumplidos
    val Fondo = Color(0xFFF5F6FA)
    val Superficie = Color(0xFFFFFFFF)
    val Texto = Color(0xFF172033)         // títulos y contenido principal (también botón oscuro)
    val TextoSecundario = Color(0xFF687386)
    val Borde = Color(0xFFE3E5EE)

    // Pasteles suaves para tarjetas (como las métricas de la referencia)
    val LavandaClaro = Color(0xFFEDEBFD)
    val LimaClaro = Color(0xFFF4FBD9)
    val CelesteClaro = Color(0xFFE4F3FB)
    val Celeste = Color(0xFF5BB8E0)
    val RosaClaro = Color(0xFFFCE8F1)
    val Rosa = Color(0xFFE5679C)

    // Estados (siempre acompañados de ícono y texto, nunca solo color)
    val Error = Color(0xFFD64545)
    val ErrorClaro = Color(0xFFFDECEC)
    val Aviso = Color(0xFFB7791F)
    val AvisoClaro = Color(0xFFFFF4DE)
}
