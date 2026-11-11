package com.udenar.runway.ui.historial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.domain.Entrenamiento
import com.udenar.runway.domain.PeriodoHistorial
import com.udenar.runway.domain.formatoCronometro
import com.udenar.runway.domain.formatoHoras
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.domain.formatoRitmo
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EncabezadoSeccion
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.components.GraficaBarras
import com.udenar.runway.ui.components.GrupoOpciones
import com.udenar.runway.ui.components.TarjetaMetrica
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.rutas.SiluetaRuta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Historial y estadísticas (especificación 5.10). */
@Composable
fun HistorialScreen(
    onVolver: () -> Unit,
    onAbrir: (String) -> Unit,
    onEntrenar: () -> Unit,
    vm: HistorialViewModel = viewModel(factory = HistorialViewModel.Factory),
) {
    val e by vm.estado.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Espacio.margen, end = Espacio.margen, bottom = Espacio.xl),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = onVolver)
                Spacer(Modifier.width(Espacio.m))
                EncabezadoPantalla("Historial", Modifier.weight(1f))
            }
        }
        if (e.cargando) return@LazyColumn
        if (!e.hayAlguno) {
            item {
                EstadoVacio(
                    icono = Icons.AutoMirrored.Outlined.DirectionsRun,
                    titulo = "Todavía no hay entrenamientos",
                    texto = "Cuando termines tu primera carrera verás aquí tu evolución, tus tiempos y tus récords.",
                    accion = "Empezar a correr",
                    onAccion = onEntrenar,
                )
            }
            return@LazyColumn
        }

        item { GrupoOpciones(PeriodoHistorial.entries, e.periodo, { it.etiqueta }, onSeleccion = vm::onPeriodo) }

        // Métricas acumuladas del periodo
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                TarjetaMetrica("Distancia", formatoKm(e.resumen.distanciaKm), "km", Icons.Outlined.Straighten,
                    fondo = Runway.LimaClaro, acento = Runway.MoradoOscuro, modifier = Modifier.weight(1f))
                TarjetaMetrica("Salidas", e.resumen.salidas.toString(), "", Icons.Outlined.Flag,
                    fondo = Runway.CelesteClaro, acento = Runway.Celeste, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                TarjetaMetrica("Tiempo", formatoHoras(e.resumen.duracionMs).substringBeforeLast(" "),
                    formatoHoras(e.resumen.duracionMs).substringAfterLast(" "), Icons.Outlined.Schedule,
                    fondo = Runway.RosaClaro, acento = Runway.Rosa, modifier = Modifier.weight(1f))
                TarjetaMetrica("Ritmo medio", formatoRitmo(e.resumen.ritmoSegKm), "/km", Icons.Outlined.Speed,
                    fondo = Runway.LavandaClaro, acento = Runway.Morado, modifier = Modifier.weight(1f))
            }
        }

        // Gráfica de evolución (una serie, un eje)
        item {
            TarjetaRunway(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (e.medida == MedidaGrafica.Distancia) "Kilómetros ${e.periodo.tituloGrafica}" else "Minutos ${e.periodo.tituloGrafica}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text("Toca una barra para ver su valor", style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario)
                    }
                }
                Spacer(Modifier.height(Espacio.s))
                GrupoOpciones(MedidaGrafica.entries, e.medida, { it.etiqueta }, onSeleccion = vm::onMedida)
                Spacer(Modifier.height(Espacio.l))
                val valores = e.barras.map { if (e.medida == MedidaGrafica.Distancia) it.km else it.minutos }
                GraficaBarras(
                    valores = valores,
                    etiquetas = e.barras.map { it.etiqueta },
                    formatoValor = { v ->
                        if (e.medida == MedidaGrafica.Distancia) "${formatoKm(v)} km" else "${v.toInt()} min"
                    },
                    descripcion = "Gráfica de ${e.medida.etiqueta.lowercase()} ${e.periodo.tituloGrafica} en ${e.periodo.etiqueta}",
                    // En 30 días se rotula una barra de cada 5 para no amontonar
                    mostrarEtiqueta = { i -> e.periodo != PeriodoHistorial.Mes || (valores.lastIndex - i) % 5 == 0 },
                )
            }
        }

        // Récords personales
        item {
            val r = e.records
            if (r.masLarga != null) {
                TarjetaRunway(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.EmojiEvents, null, tint = Runway.MoradoOscuro)
                        Spacer(Modifier.width(Espacio.s))
                        Text("Récords personales", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(Espacio.s))
                    FilaRecord("Distancia más larga", "${formatoKm(r.masLarga.distanciaM / 1000)} km", r.masLarga) { onAbrir(it) }
                    r.mejorRitmo?.let {
                        HorizontalDivider(color = Runway.Borde)
                        FilaRecord("Mejor ritmo (≥ 1 km)", "${formatoRitmo(it.ritmoPromedioSegKm)} /km", it) { id -> onAbrir(id) }
                    }
                    r.masLargaEnTiempo?.let {
                        HorizontalDivider(color = Runway.Borde)
                        FilaRecord("Mayor duración", formatoCronometro(it.duracionMs), it) { id -> onAbrir(id) }
                    }
                }
            }
        }

        // Listado cronológico
        item { EncabezadoSeccion("Entrenamientos (${e.entrenamientos.size})") }
        if (e.entrenamientos.isEmpty()) {
            item {
                Text(
                    "No hay entrenamientos en los últimos ${e.periodo.etiqueta}. Prueba con un periodo más largo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Runway.TextoSecundario,
                )
            }
        }
        items(e.entrenamientos, key = { it.id }) { ent -> TarjetaEntrenamiento(ent, onClick = { onAbrir(ent.id) }) }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun FilaRecord(etiqueta: String, valor: String, e: Entrenamiento, onAbrir: (String) -> Unit) {
    Surface(onClick = { onAbrir(e.id) }, color = Runway.Superficie) {
        Row(Modifier.fillMaxWidth().padding(vertical = Espacio.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = Runway.TextoSecundario)
                Text(fechaCorta(e.inicioEn), style = MaterialTheme.typography.labelSmall, color = Runway.TextoSecundario)
            }
            Text(valor, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Tarjeta de un entrenamiento: silueta del trazado, fecha y métricas (distancia, duración, ritmo). */
@Composable
fun TarjetaEntrenamiento(e: Entrenamiento, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Espacio.radioTarjeta),
        color = Runway.Superficie,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Espacio.l), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Espacio.radioPequeno), color = Runway.LavandaClaro, modifier = Modifier.size(64.dp)) {
                SiluetaRuta(e.puntos, Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(Espacio.l))
            Column(Modifier.weight(1f)) {
                Text(e.rutaNombre ?: "Carrera libre", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fechaLarga(e.inicioEn), style = MaterialTheme.typography.labelSmall, color = Runway.TextoSecundario)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
                    Dato(Icons.Outlined.Straighten, "${formatoKm(e.distanciaM / 1000)} km")
                    Dato(Icons.Outlined.Schedule, formatoCronometro(e.duracionMs))
                    Dato(Icons.Outlined.Speed, formatoRitmo(e.ritmoPromedioSegKm))
                }
            }
            if (e.pendienteSync) {
                // Pendiente de sincronizar: indicador discreto con ícono (no solo color)
                Icon(Icons.Outlined.CloudSync, contentDescription = "Pendiente de sincronizar", tint = Runway.TextoSecundario, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Dato(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icono, null, tint = Runway.TextoSecundario, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(texto, style = MaterialTheme.typography.labelMedium)
    }
}

private val ES = Locale.forLanguageTag("es-CO")
fun fechaLarga(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, ES).format(Date(ms))
fun fechaCorta(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM, ES).format(Date(ms))
