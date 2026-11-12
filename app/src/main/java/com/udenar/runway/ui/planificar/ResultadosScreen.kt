package com.udenar.runway.ui.planificar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EstadoCargando
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.mapa.TrazoRuta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Resultados y comparación de rutas (especificación 5.5). */
@Composable
fun ResultadosScreen(vm: PlanificadorViewModel, onVolver: () -> Unit, onDetalle: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val inicio = e.inicio
    val camara = remember { PosicionCamara(inicio?.coordenada ?: com.udenar.runway.domain.CAMARA_POR_DEFECTO.centro, 14.0) }
    val trazos = e.rutas.mapIndexed { i, r -> TrazoRuta(r.id, r.puntos, colorDeRuta(i), r.id == e.seleccionada) }
    val seleccionada = e.rutaSeleccionada

    Column(Modifier.fillMaxSize()) {
        // Mapa con todos los trazados candidatos
        Box(Modifier.fillMaxWidth().weight(0.45f)) {
            MapaRunway(
                camaraInicial = camara,
                puntoSeleccionado = inicio?.coordenada,
                destinoRuta = e.destino?.coordenada,
                rutas = trazos,
                finRuta = seleccionada?.takeIf { it.modalidad == com.udenar.runway.domain.Modalidad.Lineal }?.fin,
                comandos = vm.comandosMapa,
                onEstiloCargado = vm::encuadrarTodas,
                onToqueRuta = vm::seleccionar,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.statusBarsPadding().padding(Espacio.l),
            ) {
                BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Cambiar criterios", onClick = onVolver, sombra = 4.dp)
                Spacer(Modifier.width(Espacio.s))
                Surface(shape = CircleShape, color = Runway.Superficie, shadowElevation = 4.dp) {
                    Text(
                        e.destino?.let { "${e.modalidad.etiqueta} · hasta ${it.nombre}" }
                            ?: "${formatoKm(e.distanciaKm)} km · ${e.modalidad.etiqueta.lowercase()} · ${e.desnivel.etiqueta.lowercase()}",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = Espacio.l, vertical = Espacio.m),
                    )
                }
            }
        }

        // Lista de alternativas
        Surface(
            shape = RoundedCornerShape(topStart = Espacio.radioTarjeta, topEnd = Espacio.radioTarjeta),
            color = Runway.Fondo,
            modifier = Modifier.fillMaxWidth().weight(0.55f),
        ) {
            when (val calculo = e.calculo) {
                is EstadoCalculo.Calculando, EstadoCalculo.Inactivo -> Column(
                    Modifier.fillMaxSize().padding(Espacio.margen),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val paso = (calculo as? EstadoCalculo.Calculando)?.takeIf { it.paso > 0 }
                    EstadoCargando(if (paso == null) "Buscando recorridos…" else "Buscando recorridos… (${paso.paso} de ${paso.total})")
                    Text(
                        "Probamos varias direcciones desde tu punto de inicio y elegimos las que mejor se ajustan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Runway.TextoSecundario,
                        modifier = Modifier.padding(horizontal = Espacio.xl),
                    )
                }
                EstadoCalculo.Error -> EstadoVacio(
                    icono = Icons.Outlined.CloudOff,
                    titulo = "No fue posible calcular rutas",
                    texto = "El servicio de rutas no respondió. Revisa tu conexión e inténtalo de nuevo.",
                    accion = "Reintentar",
                    onAccion = vm::buscarRutas,
                    modifier = Modifier.padding(Espacio.margen),
                )
                EstadoCalculo.SinRutas -> EstadoVacio(
                    icono = Icons.Outlined.SearchOff,
                    titulo = "No encontramos recorridos",
                    texto = "Prueba con otra distancia o con un punto de inicio sobre una calle o camino.",
                    accion = "Cambiar criterios",
                    onAccion = onVolver,
                    modifier = Modifier.padding(Espacio.margen),
                )
                EstadoCalculo.Listo -> LazyColumn(
                    contentPadding = PaddingValues(Espacio.l),
                    verticalArrangement = Arrangement.spacedBy(Espacio.m),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (e.rutas.size == 1) "1 alternativa" else "${e.rutas.size} alternativas",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    "Ordenadas por ajuste a tu distancia y desnivel. Toca una en el mapa o en la lista.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Runway.TextoSecundario,
                                )
                            }
                            BotonCircular(Icons.Outlined.Tune, "Modificar criterios", onClick = onVolver)
                        }
                    }
                    itemsIndexed(e.rutas, key = { _, r -> r.id }) { i, ruta ->
                        TarjetaAlternativa(
                            ruta = ruta,
                            colorHex = colorDeRuta(i),
                            recomendada = i == 0,
                            seleccionada = ruta.id == e.seleccionada,
                            onClick = { vm.seleccionar(ruta.id) },
                        ) {
                            BotonRunway(
                                "Ver detalle",
                                onClick = onDetalle,
                                estilo = EstiloBoton.Suave,
                                icono = Icons.AutoMirrored.Outlined.ArrowForward,
                                modifier = Modifier.fillMaxWidth(),
                                alto = 48.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}
