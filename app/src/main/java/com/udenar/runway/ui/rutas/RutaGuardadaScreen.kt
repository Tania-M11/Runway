package com.udenar.runway.ui.rutas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.domain.RutaReferencia
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.entrenar.rememberIniciarEntrenamiento
import com.udenar.runway.ui.components.EstadoCargando
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.GrupoOpciones
import com.udenar.runway.ui.planificar.COLORES_RUTAS
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Detalle de una ruta de Mis rutas (especificación 5.11). */
@Composable
fun RutaGuardadaScreen(
    onVolver: () -> Unit,
    onEntrenar: () -> Unit,
    vm: RutaGuardadaViewModel = viewModel(factory = RutaGuardadaViewModel.Factory),
) {
    val guardada by vm.ruta.collectAsStateWithLifecycle()
    var renombrando by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf(false) }
    val iniciar = rememberIniciarEntrenamiento(onIniciado = onEntrenar)

    val g = guardada ?: run {
        EstadoCargando("Cargando ruta…", Modifier.fillMaxSize())
        return
    }
    val ruta = remember(g) { g.comoCandidata(vm.nivel) }

    PantallaDetalleRuta(
        ruta = ruta,
        colorHex = COLORES_RUTAS.first(),
        inicioNombre = g.inicioNombre,
        finNombre = g.finNombre ?: ruta.fin.texto(),
        comandos = vm.comandosMapa,
        onMapaListo = vm::encuadrar,
        onVolver = onVolver,
        descripcionVolver = "Volver a Mis rutas",
        encabezado = {
            Spacer(Modifier.height(Espacio.m))
            Text("Visibilidad", style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
            Spacer(Modifier.height(Espacio.xs))
            GrupoOpciones(VISIBILIDADES_DISPONIBLES, g.visibilidad, { it.etiqueta }, onSeleccion = vm::cambiarVisibilidad)
            Text(
                g.visibilidad.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = Runway.TextoSecundario,
                modifier = Modifier.padding(top = Espacio.xs),
            )
        },
    ) {
        BotonRunway(
            "Entrenar con esta ruta",
            onClick = { iniciar(RutaReferencia(g.id, g.nombre, g.puntos)) },
            icono = Icons.AutoMirrored.Outlined.DirectionsRun,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Espacio.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
            BotonRunway(
                "Renombrar",
                onClick = { renombrando = true },
                icono = Icons.Outlined.Edit,
                estilo = EstiloBoton.Contorno,
                alto = 48.dp,
                modifier = Modifier.weight(1f),
            )
            BotonRunway(
                "Eliminar",
                onClick = { eliminando = true },
                icono = Icons.Outlined.DeleteOutline,
                estilo = EstiloBoton.Contorno,
                alto = 48.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (renombrando) {
        DialogoRenombrar(
            nombreActual = g.nombre,
            onConfirmar = {
                vm.renombrar(it)
                renombrando = false
            },
            onCerrar = { renombrando = false },
        )
    }
    if (eliminando) {
        DialogoEliminar(
            nombre = g.nombre,
            onConfirmar = {
                eliminando = false
                vm.eliminar(alTerminar = onVolver)
            },
            onCerrar = { eliminando = false },
        )
    }
}
