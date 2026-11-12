package com.udenar.runway.ui.entrenar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.domain.EstadoEntrenamiento
import com.udenar.runway.domain.formatoCronometro
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaDestacada
import com.udenar.runway.ui.components.TarjetaOpcion
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import com.udenar.runway.ui.ubicacion.abrirAjustesDeUbicacion
import com.udenar.runway.domain.formatoMetros
import com.udenar.runway.data.ubicacion.EstadoSatelites

/** Pestaña Entrenar: elegir carrera libre o una ruta guardada y empezar. */
@Composable
fun EntrenarScreen(
    onAbrirEntrenamiento: () -> Unit,
    vm: EntrenarViewModel = viewModel(factory = EntrenarViewModel.Factory),
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val iniciar = rememberIniciarEntrenamiento(onIniciado = onAbrirEntrenamiento)

    LaunchedEffect(e.sesion.enCurso) {
        if (!e.sesion.enCurso) ciclo.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.vigilarGps() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacio.margen)
            .padding(bottom = Espacio.xl),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        EncabezadoPantalla("Entrenar")

        if (e.sesion.enCurso) {
            // Hay una sesión en curso (o recuperada tras un cierre): se retoma, no se crea otra
            val pausado = e.sesion.estado == EstadoEntrenamiento.Pausado
            TarjetaDestacada(
                titulo = if (pausado) "Entrenamiento pausado" else "Entrenamiento en curso",
                texto = buildString {
                    append("${formatoKm(e.sesion.distanciaM / 1000)} km · ${formatoCronometro(e.sesion.duracionMs(System.currentTimeMillis()))}")
                    if (e.sesion.recuperada) append("\nLo recuperamos después de que la app se cerró.")
                },
                icono = Icons.AutoMirrored.Outlined.DirectionsRun,
            ) {
                BotonRunway("Volver al entrenamiento", onAbrirEntrenamiento, estilo = EstiloBoton.Lima, modifier = Modifier.fillMaxWidth())
            }
            return@Column
        }

        TarjetaGps(e.gps, e.precisionM, e.satelites, onActivar = { context.abrirAjustesDeUbicacion() })

        Text("¿Cómo quieres correr?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Espacio.s))
        TarjetaOpcion(
            "Carrera libre",
            "Corre por donde quieras; registramos tu recorrido",
            seleccionada = e.rutaElegida == null,
            onClick = { vm.elegir(null) },
        )
        if (e.rutas.isEmpty()) {
            Text(
                "Cuando guardes rutas en Mis rutas podrás elegirlas aquí para seguirlas en el mapa.",
                style = MaterialTheme.typography.bodySmall,
                color = Runway.TextoSecundario,
            )
        } else {
            Text("O sigue una de tus rutas", style = MaterialTheme.typography.labelMedium, color = Runway.TextoSecundario)
            e.rutas.forEach { r ->
                TarjetaOpcion(
                    r.nombre,
                    "${formatoKm(r.distanciaKm)} km · ↗ ${formatoMetros(r.subidaM)} m · ${r.visibilidad.etiqueta.lowercase()}",
                    seleccionada = e.rutaElegida == r.id,
                    onClick = { vm.elegir(r.id) },
                )
            }
        }

        Spacer(Modifier.height(Espacio.s))
        BotonRunway(
            "Empezar",
            onClick = { iniciar(vm.referenciaElegida()) },
            icono = Icons.AutoMirrored.Outlined.DirectionsRun,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        )
        Text(
            "Se registra aunque no tengas internet y con la pantalla apagada.",
            style = MaterialTheme.typography.bodySmall,
            color = Runway.TextoSecundario,
        )
    }
}

@Composable
private fun TarjetaGps(gps: EstadoGps, precision: Float?, satelites: EstadoSatelites?, onActivar: () -> Unit) {
    val (icono, titulo, texto, color) = when (gps) {
        EstadoGps.Listo -> Cuatro(Icons.Outlined.GpsFixed, "GPS listo", "Precisión de ±${precision?.toInt() ?: "-"} m", Runway.LimaClaro)
        EstadoGps.Debil -> Cuatro(Icons.Outlined.GpsNotFixed, "Señal GPS débil", "Al aire libre la señal mejora en unos segundos", Runway.AvisoClaro)
        EstadoGps.Buscando -> Cuatro(Icons.Outlined.GpsNotFixed, "Buscando señal GPS…", "Puedes empezar; la señal se ajusta al moverte", Runway.Fondo)
        EstadoGps.Apagado -> Cuatro(Icons.Outlined.GpsOff, "Ubicación apagada", "Actívala para registrar tu recorrido", Runway.AvisoClaro)
        EstadoGps.SinPermiso -> Cuatro(Icons.Outlined.GpsOff, "Sin permiso de ubicación", "Te lo pediremos al empezar", Runway.AvisoClaro)
    }
    TarjetaRunway(Modifier.fillMaxWidth(), color = color, relleno = Espacio.l) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Runway.Superficie, modifier = Modifier.size(40.dp)) {
                Icon(icono, null, tint = Runway.Texto, modifier = Modifier.padding(9.dp))
            }
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleSmall)
                Text(texto, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario)
                // Hardware GNSS: satélites usados de los visibles y por constelación
                satelites?.takeIf { it.visibles > 0 }?.let { s ->
                    Text(
                        "Satélites: ${s.usados} en uso de ${s.visibles} visibles" +
                            if (s.porSistema.isNotEmpty()) " · " + s.porSistema.entries.sortedByDescending { it.value }
                                .joinToString(" · ") { "${it.key} ${it.value}" } else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Runway.MoradoOscuro,
                    )
                }
            }
            if (gps == EstadoGps.Apagado) {
                androidx.compose.material3.TextButton(onClick = onActivar) { Text("Activar", color = Runway.MoradoOscuro) }
            }
        }
    }
}

private data class Cuatro(
    val icono: androidx.compose.ui.graphics.vector.ImageVector,
    val titulo: String,
    val texto: String,
    val color: androidx.compose.ui.graphics.Color,
)
