package com.udenar.runway.ui.planificar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.formatoDuracion
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.domain.formatoMetros
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaMetrica
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.mapa.TrazoRuta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Detalle de ruta (especificación 5.6). */
@Composable
fun DetalleRutaScreen(vm: PlanificadorViewModel, onVolver: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val ruta = e.rutaSeleccionada ?: run {
        LaunchedEffect(Unit) { onVolver() }
        return
    }
    val indice = e.rutas.indexOf(ruta)
    val color = colorDeRuta(indice)
    val camara = remember(ruta.id) { PosicionCamara(ruta.inicio, 14.0) }
    LaunchedEffect(ruta.id) { vm.prepararDetalle() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            MapaRunway(
                camaraInicial = camara,
                puntoSeleccionado = ruta.inicio,
                finRuta = ruta.fin.takeIf { ruta.modalidad == Modalidad.Lineal },
                rutas = listOf(TrazoRuta(ruta.id, ruta.puntos, color, seleccionada = true)),
                comandos = vm.comandosMapa,
                onEstiloCargado = vm::encuadrarSeleccionada,
                modifier = Modifier.fillMaxSize(),
            )
            BotonCircular(
                Icons.AutoMirrored.Outlined.ArrowBack,
                "Volver a resultados",
                onClick = onVolver,
                sombra = 4.dp,
                modifier = Modifier.statusBarsPadding().padding(Espacio.l),
            )
        }

        Column(Modifier.padding(horizontal = Espacio.margen).navigationBarsPadding()) {
            Spacer(Modifier.height(Espacio.l))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(ruta.modalidad.etiqueta, style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
                    Text(ruta.nombre, style = MaterialTheme.typography.headlineSmall)
                }
                EtiquetaExigencia(ruta.exigencia)
            }

            Spacer(Modifier.height(Espacio.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                TarjetaMetrica(
                    "Distancia", formatoKm(ruta.distanciaKm), "km", Icons.Outlined.Straighten,
                    fondo = Runway.LimaClaro, acento = Runway.MoradoOscuro, modifier = Modifier.weight(1f),
                )
                TarjetaMetrica(
                    "Tiempo aprox.", formatoDuracion(ruta.tiempoMin).removeSuffix(" min"),
                    if (ruta.tiempoMin < 60) "min" else "", Icons.Outlined.Schedule,
                    fondo = Runway.CelesteClaro, acento = Runway.Celeste, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Espacio.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                TarjetaMetrica(
                    "Subida", formatoMetros(ruta.subidaM), "m", Icons.AutoMirrored.Outlined.TrendingUp,
                    fondo = Runway.RosaClaro, acento = Runway.Rosa, modifier = Modifier.weight(1f),
                )
                TarjetaMetrica(
                    "Bajada", formatoMetros(ruta.bajadaM), "m", Icons.AutoMirrored.Outlined.TrendingDown,
                    fondo = Runway.LavandaClaro, acento = Runway.Morado, modifier = Modifier.weight(1f),
                )
            }

            if (ruta.elevaciones.size > 1) {
                Spacer(Modifier.height(Espacio.l))
                TarjetaRunway(Modifier.fillMaxWidth()) {
                    Text("Perfil de elevación", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Espacio.s))
                    PerfilElevacion(ruta.elevaciones, color.aColor())
                }
            }

            Spacer(Modifier.height(Espacio.l))
            TarjetaRunway(Modifier.fillMaxWidth()) {
                FilaInfo("Inicio", e.inicio?.nombre ?: ruta.inicio.texto())
                HorizontalDivider(color = Runway.Borde)
                FilaInfo(
                    "Final",
                    if (ruta.modalidad == Modalidad.Circular) "El mismo punto de inicio" else e.nombreFin ?: "Buscando…",
                )
                if (ruta.calles.isNotEmpty()) {
                    HorizontalDivider(color = Runway.Borde)
                    FilaInfo("Vías principales", ruta.calles.joinToString(", "))
                }
            }

            Spacer(Modifier.height(Espacio.xl))
            // El entrenamiento y el guardado llegan en las partes 5 y 6: se muestran desactivados
            BotonRunway(
                "Iniciar entrenamiento",
                onClick = {},
                icono = Icons.AutoMirrored.Outlined.DirectionsRun,
                habilitado = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Espacio.s))
            BotonRunway(
                "Guardar ruta",
                onClick = {},
                icono = Icons.Outlined.BookmarkAdd,
                estilo = EstiloBoton.Contorno,
                habilitado = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Guardar rutas e iniciar entrenamientos estarán disponibles pronto.",
                style = MaterialTheme.typography.bodySmall,
                color = Runway.TextoSecundario,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = Espacio.m),
            )
            Spacer(Modifier.height(Espacio.l))
        }
    }
    // Franja bajo la barra de estado para que el contenido no choque con la hora al desplazar
    Spacer(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(Runway.Fondo.copy(alpha = 0.85f)),
    )
    }
}

@Composable
private fun FilaInfo(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Espacio.m)) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = Runway.TextoSecundario, modifier = Modifier.width(120.dp))
        Text(valor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
