package com.udenar.runway.ui.rutas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.RutaGuardada
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.domain.formatoMetros
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.components.GrupoOpciones
import com.udenar.runway.ui.planificar.DatoRuta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos

/** Mis rutas (especificación 5.11): catálogo propio, guardado en el teléfono. */
@Composable
fun MisRutasScreen(
    onAbrir: (String) -> Unit,
    onPlanificar: () -> Unit,
    vm: MisRutasViewModel = viewModel(factory = MisRutasViewModel.Factory),
) {
    val e by vm.estado.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = Espacio.margen)) {
            EncabezadoPantalla(
                "Mis rutas",
                subtitulo = if (e.todas.isEmpty()) null else "${e.todas.size} guardadas en este teléfono",
            )
            Spacer(Modifier.height(Espacio.s))
            GrupoOpciones(FiltroRutas.entries, e.filtro, { it.etiqueta }, onSeleccion = vm::onFiltro)
        }

        when {
            e.cargando -> Unit
            e.filtro == FiltroRutas.Compartidas -> EstadoVacio(
                icono = Icons.Outlined.Groups,
                titulo = "Rutas de la comunidad",
                texto = "Publicar rutas para otros corredores estará disponible más adelante. Por ahora tus rutas son solo tuyas.",
                modifier = Modifier.padding(Espacio.margen),
            )
            e.visibles.isEmpty() -> EstadoVacio(
                icono = Icons.Outlined.Bookmarks,
                titulo = if (e.todas.isEmpty()) "Aún no tienes rutas guardadas" else "No hay rutas ${e.filtro.etiqueta.lowercase()}",
                texto = "Planifica un recorrido y guárdalo para repetirlo cuando quieras.",
                accion = "Planificar ruta",
                onAccion = onPlanificar,
                modifier = Modifier.padding(Espacio.margen),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(Espacio.margen),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                items(e.visibles, key = { it.id }) { ruta -> TarjetaRutaGuardada(ruta, onClick = { onAbrir(ruta.id) }) }
            }
        }
    }
}

@Composable
private fun TarjetaRutaGuardada(ruta: RutaGuardada, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Espacio.radioTarjeta),
        color = Runway.Superficie,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Espacio.l), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Espacio.radioPequeno), color = Runway.LavandaClaro, modifier = Modifier.size(72.dp)) {
                SiluetaRuta(ruta.puntos, Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(Espacio.l))
            Column(Modifier.weight(1f)) {
                Text(ruta.nombre, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(Espacio.m)) {
                    DatoRuta(Icons.Outlined.Straighten, "${formatoKm(ruta.distanciaKm)} km")
                    DatoRuta(Icons.AutoMirrored.Outlined.TrendingUp, "${formatoMetros(ruta.subidaM)} m")
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EtiquetaVisibilidad(ruta.visibilidad)
                    Spacer(Modifier.width(Espacio.s))
                    Text(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ruta.creadaEn)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Runway.TextoSecundario,
                    )
                }
            }
        }
    }
}

/** Indicador de visibilidad: ícono + texto (especificación 5.11). */
@Composable
fun EtiquetaVisibilidad(v: com.udenar.runway.domain.Visibilidad) {
    Surface(shape = CircleShape, color = Runway.Fondo, contentColor = Runway.MoradoOscuro) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Icon(v.icono, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(v.etiqueta, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Dibujo de la forma de la ruta (sin mapa): ligero para listas largas. */
@Composable
fun SiluetaRuta(puntos: List<Coordenada>, modifier: Modifier = Modifier) {
    if (puntos.size < 2) return
    val color = Runway.Morado
    Canvas(modifier.fillMaxSize()) {
        // Proyección simple: la longitud se corrige por la latitud para no deformar la forma
        val k = cos(Math.toRadians(puntos.first().lat))
        val xs = puntos.map { it.lon * k }
        val ys = puntos.map { it.lat }
        val ancho = (xs.max() - xs.min()).coerceAtLeast(1e-9)
        val alto = (ys.max() - ys.min()).coerceAtLeast(1e-9)
        val escala = minOf(size.width / ancho, size.height / alto)
        val dx = (size.width - ancho * escala) / 2
        val dy = (size.height - alto * escala) / 2
        fun p(i: Int) = Offset(
            (dx + (xs[i] - xs.min()) * escala).toFloat(),
            (dy + (ys.max() - ys[i]) * escala).toFloat(),
        )
        val camino = Path().apply {
            moveTo(p(0).x, p(0).y)
            for (i in 1 until puntos.size) lineTo(p(i).x, p(i).y)
        }
        drawPath(camino, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Runway.Texto, radius = 4.dp.toPx(), center = p(0))
    }
}
