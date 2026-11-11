package com.udenar.runway.ui.planificar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.domain.RutaReferencia
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.entrenar.rememberIniciarEntrenamiento
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.rutas.HojaGuardarRuta
import com.udenar.runway.ui.rutas.PantallaDetalleRuta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Detalle de una alternativa recién calculada (especificación 5.6), con opción de guardarla. */
@Composable
fun DetalleRutaScreen(
    vm: PlanificadorViewModel,
    onVolver: () -> Unit,
    onVerMisRutas: () -> Unit,
    onEntrenar: () -> Unit,
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val ruta = e.rutaSeleccionada ?: run {
        LaunchedEffect(Unit) { onVolver() }
        return
    }
    var mostrarGuardar by remember { mutableStateOf(false) }
    val iniciar = rememberIniciarEntrenamiento(onIniciado = onEntrenar)
    val guardadaComo = e.guardadas[ruta.id]
    LaunchedEffect(ruta.id) { vm.prepararDetalle() }
    // Cierra la hoja en cuanto termina de guardar
    LaunchedEffect(guardadaComo) { if (guardadaComo != null) mostrarGuardar = false }

    PantallaDetalleRuta(
        ruta = ruta,
        colorHex = colorDeRuta(e.rutas.indexOf(ruta)),
        inicioNombre = e.inicio?.nombre ?: ruta.inicio.texto(),
        finNombre = e.nombreFin,
        comandos = vm.comandosMapa,
        onMapaListo = vm::encuadrarSeleccionada,
        onVolver = onVolver,
        descripcionVolver = "Volver a resultados",
    ) {
        BotonRunway(
            "Iniciar entrenamiento",
            onClick = { iniciar(RutaReferencia(null, ruta.nombre, ruta.puntos)) },
            icono = Icons.AutoMirrored.Outlined.DirectionsRun,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Espacio.s))
        if (guardadaComo == null) {
            BotonRunway(
                "Guardar ruta",
                onClick = { mostrarGuardar = true },
                icono = Icons.Outlined.BookmarkAdd,
                estilo = EstiloBoton.Contorno,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Confirmación visual del guardado local (especificación, sección 7)
            TarjetaRunway(Modifier.fillMaxWidth(), color = Runway.LimaClaro, relleno = Espacio.l) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Runway.Texto)
                    Spacer(Modifier.width(Espacio.s))
                    Text(
                        "Guardada en Mis rutas como ${guardadaComo.etiqueta.lowercase()}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.height(Espacio.m))
                BotonRunway("Ver Mis rutas", onClick = onVerMisRutas, estilo = EstiloBoton.Oscuro, alto = 48.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (mostrarGuardar) {
        HojaGuardarRuta(
            nombreInicial = ruta.nombre,
            guardando = e.guardando,
            onGuardar = vm::guardarRuta,
            onCerrar = { mostrarGuardar = false },
        )
    }
}
