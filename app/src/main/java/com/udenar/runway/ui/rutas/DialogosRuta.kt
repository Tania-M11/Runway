package com.udenar.runway.ui.rutas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.udenar.runway.domain.Visibilidad
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.CampoTexto
import com.udenar.runway.ui.components.TarjetaOpcion
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

const val MAX_NOMBRE_RUTA = 40

val Visibilidad.icono: ImageVector
    get() = when (this) {
        Visibilidad.Privada -> Icons.Outlined.Lock
        Visibilidad.Reutilizable -> Icons.Outlined.Repeat
        Visibilidad.Compartida -> Icons.Outlined.Groups
    }

/** Visibilidades que ya se pueden elegir (compartir con la comunidad llega más adelante). */
val VISIBILIDADES_DISPONIBLES = listOf(Visibilidad.Privada, Visibilidad.Reutilizable)

/**
 * Hoja para guardar una ruta (especificación 5.6 y flujo 6.3): nombre + visibilidad.
 * El botón dice explícitamente con qué visibilidad se guarda (confirmación clara).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaGuardarRuta(
    nombreInicial: String,
    guardando: Boolean,
    onGuardar: (nombre: String, visibilidad: Visibilidad) -> Unit,
    onCerrar: () -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial.take(MAX_NOMBRE_RUTA)) }
    var visibilidad by remember { mutableStateOf(Visibilidad.Privada) }
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Runway.Fondo,
    ) {
        Column(
            Modifier
                .padding(horizontal = Espacio.margen)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Text("Guardar ruta", style = MaterialTheme.typography.headlineSmall)
            CampoTexto(
                valor = nombre,
                onValor = { nombre = it.take(MAX_NOMBRE_RUTA) },
                etiqueta = "Nombre",
                ayuda = "${nombre.length}/$MAX_NOMBRE_RUTA",
            )
            Text("¿Quién puede verla?", style = MaterialTheme.typography.titleMedium)
            VISIBILIDADES_DISPONIBLES.forEach { v ->
                TarjetaOpcion(v.etiqueta, v.descripcion, visibilidad == v, onClick = { visibilidad = v })
            }
            Text(
                "Compartir rutas con la comunidad estará disponible más adelante.",
                style = MaterialTheme.typography.bodySmall,
                color = Runway.TextoSecundario,
            )
            BotonRunway(
                "Guardar como ${visibilidad.etiqueta.lowercase()}",
                onClick = { onGuardar(nombre, visibilidad) },
                icono = visibilidad.icono,
                habilitado = nombre.isNotBlank(),
                cargando = guardando,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Espacio.l))
        }
    }
}

/** Diálogo para cambiar el nombre de una ruta guardada. */
@Composable
fun DialogoRenombrar(nombreActual: String, onConfirmar: (String) -> Unit, onCerrar: () -> Unit) {
    var nombre by remember { mutableStateOf(nombreActual) }
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Runway.Fondo,
        title = { Text("Cambiar nombre") },
        text = {
            CampoTexto(nombre, { nombre = it.take(MAX_NOMBRE_RUTA) }, "Nombre", ayuda = "${nombre.length}/$MAX_NOMBRE_RUTA")
        },
        confirmButton = {
            TextButton(onClick = { onConfirmar(nombre) }, enabled = nombre.isNotBlank()) {
                Text("Guardar", color = Runway.Morado)
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = Runway.TextoSecundario) } },
    )
}

/** Confirmación antes de una acción destructiva (especificación, sección 8). */
@Composable
fun DialogoEliminar(nombre: String, onConfirmar: () -> Unit, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Runway.Fondo,
        title = { Text("¿Eliminar ruta?") },
        text = { Text("«$nombre» se quitará de Mis rutas. Esta acción no se puede deshacer.") },
        confirmButton = { TextButton(onClick = onConfirmar) { Text("Eliminar", color = Runway.Error) } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = Runway.TextoSecundario) } },
    )
}
