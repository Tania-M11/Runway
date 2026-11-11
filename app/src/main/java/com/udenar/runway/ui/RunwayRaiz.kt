package com.udenar.runway.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.udenar.runway.ui.theme.RunwayTheme

/**
 * Raíz de la interfaz. Por ahora es una pantalla provisional; la navegación
 * (Mapa, Planificar, Entrenar, Historial, Mis rutas) llega con el diseño.
 */
@Composable
fun RunwayRaiz() {
    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("RUNWAY", style = MaterialTheme.typography.displayMedium)
            Text(
                "Base del proyecto lista.\nSiguiente: mapa de Ipiales.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RunwayRaizPreview() {
    RunwayTheme { RunwayRaiz() }
}
