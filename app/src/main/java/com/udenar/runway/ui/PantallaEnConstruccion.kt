package com.udenar.runway.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.theme.Espacio

/**
 * Marcador temporal mientras se construye cada sección. Dice claramente que aún no
 * está disponible (la especificación pide no simular funciones como terminadas).
 */
@Composable
fun PantallaEnConstruccion(titulo: String, icono: ImageVector, descripcion: String) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Espacio.margen),
    ) {
        EncabezadoPantalla(titulo)
        EstadoVacio(
            icono = icono,
            titulo = "Disponible pronto",
            texto = descripcion,
        )
    }
}
