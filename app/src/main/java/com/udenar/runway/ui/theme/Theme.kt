package com.udenar.runway.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Tema provisional: se reemplaza con la paleta y tipografía del diseño de RUNWAY.
private val Claro = lightColorScheme()
private val Oscuro = darkColorScheme()

@Composable
fun RunwayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        content = content,
    )
}
