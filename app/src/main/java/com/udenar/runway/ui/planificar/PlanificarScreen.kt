package com.udenar.runway.ui.planificar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.PreferenciaDesnivel
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BarraBusqueda
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EstadoCargando
import com.udenar.runway.ui.components.FilaLugar
import com.udenar.runway.ui.components.GrupoOpciones
import com.udenar.runway.ui.components.TarjetaOpcion
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import com.udenar.runway.ui.ubicacion.ResultadoPermiso
import com.udenar.runway.ui.ubicacion.abrirAjustesDeLaApp
import com.udenar.runway.ui.ubicacion.abrirAjustesDeUbicacion
import com.udenar.runway.ui.ubicacion.rememberPedirPermisoUbicacion

private val DISTANCIAS_RAPIDAS = listOf(3.0, 5.0, 10.0, 21.0)

/** Planificar ruta (especificación 5.4). */
@Composable
fun PlanificarScreen(
    vm: PlanificadorViewModel,
    onVolver: () -> Unit,
    onBuscar: () -> Unit,
    onElegirPunto: (ObjetivoPunto) -> Unit,
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val foco = LocalFocusManager.current
    val pedirPermiso = rememberPedirPermisoUbicacion { r ->
        if (r == ResultadoPermiso.DenegadoPermanente) context.abrirAjustesDeLaApp()
        vm.usarMiUbicacion()
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Espacio.margen),
        ) {
            BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = onVolver)
            Spacer(Modifier.width(Espacio.m))
            EncabezadoPantalla("Planificar ruta", Modifier.weight(1f))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margen),
        ) {
            // --- Punto de inicio
            Seccion("Punto de inicio")
            val origenes = OrigenInicio.entries.filter { it != OrigenInicio.PuntoMapa || e.puntoMapa != null }
            GrupoOpciones(origenes, e.origen, { it.etiqueta }, onSeleccion = { o ->
                vm.elegirOrigen(o)
                if (o == OrigenInicio.Mapa) {
                    vm.abrirSelector(ObjetivoPunto.Inicio)
                    onElegirPunto(ObjetivoPunto.Inicio)
                }
            })
            Spacer(Modifier.height(Espacio.m))
            when {
                e.origen == OrigenInicio.Buscar && e.inicio == null -> {
                    BarraBusqueda(e.consulta, vm::onConsulta, "Ciudad, barrio o parque")
                    when {
                        e.buscandoLugar -> EstadoCargando("Buscando lugares…")
                        e.mensajeBusqueda != null -> Text(
                            e.mensajeBusqueda!!, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario,
                            modifier = Modifier.padding(top = Espacio.s),
                        )
                        e.lugaresEncontrados.isNotEmpty() -> TarjetaRunway(Modifier.fillMaxWidth().padding(top = Espacio.s), relleno = 0.dp) {
                            e.lugaresEncontrados.forEachIndexed { i, lugar ->
                                FilaLugar(lugar) {
                                    foco.clearFocus()
                                    vm.elegirLugarBuscado(lugar)
                                }
                                if (i < e.lugaresEncontrados.lastIndex) HorizontalDivider(color = Runway.Borde)
                            }
                        }
                    }
                }
                e.ubicandome -> EstadoCargando("Obteniendo tu ubicación…")
                e.avisoInicio == AvisoInicio.SinPermiso -> AvisoEstado(
                    TipoAviso.SinUbicacion, "Sin permiso de ubicación. Permítelo o busca un lugar.",
                    accion = "Permitir", onAccion = pedirPermiso,
                )
                e.avisoInicio == AvisoInicio.UbicacionApagada -> AvisoEstado(
                    TipoAviso.SinUbicacion, "La ubicación del teléfono está apagada.",
                    accion = "Activar", onAccion = { context.abrirAjustesDeUbicacion() },
                )
                e.avisoInicio == AvisoInicio.SinSenal -> AvisoEstado(
                    TipoAviso.GpsDebil, "No logramos ubicarte. Intenta al aire libre o busca un lugar.",
                    accion = "Reintentar", onAccion = vm::usarMiUbicacion,
                )
                e.inicio != null -> TarjetaInicio(
                    nombre = e.inicio!!.nombre,
                    detalle = e.inicio!!.detalle,
                    onCambiar = when (e.origen) {
                        OrigenInicio.Buscar -> ({ vm.elegirOrigen(OrigenInicio.Buscar) })
                        OrigenInicio.Mapa -> ({
                            vm.abrirSelector(ObjetivoPunto.Inicio)
                            onElegirPunto(ObjetivoPunto.Inicio)
                        })
                        else -> null
                    },
                )
            }

            // --- Destino (opcional): elegirlo en el mapa, buscarlo o tocar una sugerencia
            Seccion("Destino (opcional)")
            GrupoOpciones(OrigenDestino.entries, e.origenDestino, { it.etiqueta }, onSeleccion = { o ->
                vm.elegirOrigenDestino(o)
                if (o == OrigenDestino.Mapa) {
                    vm.abrirSelector(ObjetivoPunto.Destino)
                    onElegirPunto(ObjetivoPunto.Destino)
                }
            })
            Spacer(Modifier.height(Espacio.m))
            val destinoElegido = e.destino
            when {
                destinoElegido != null -> TarjetaDestino(
                    nombre = destinoElegido.nombre,
                    detalle = e.inicio?.let { i ->
                        "A ${formatoKm(com.udenar.runway.domain.distanciaKm(i.coordenada, destinoElegido.coordenada))} km en línea recta"
                    } ?: destinoElegido.detalle,
                    onCambiar = {
                        vm.abrirSelector(ObjetivoPunto.Destino)
                        onElegirPunto(ObjetivoPunto.Destino)
                    },
                    onQuitar = vm::quitarDestino,
                )
                e.origenDestino == OrigenDestino.Buscar -> {
                    BarraBusqueda(e.consultaDestino, vm::onConsultaDestino, "¿A dónde quieres llegar?")
                    when {
                        e.buscandoDestino -> EstadoCargando("Buscando lugares…")
                        e.mensajeBusqueda != null -> Text(
                            e.mensajeBusqueda!!, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario,
                            modifier = Modifier.padding(top = Espacio.s),
                        )
                        e.lugaresDestino.isNotEmpty() -> TarjetaRunway(Modifier.fillMaxWidth().padding(top = Espacio.s), relleno = 0.dp) {
                            e.lugaresDestino.forEachIndexed { i, lugar ->
                                FilaLugar(lugar) {
                                    foco.clearFocus()
                                    vm.elegirLugarDestino(lugar)
                                }
                                if (i < e.lugaresDestino.lastIndex) HorizontalDivider(color = Runway.Borde)
                            }
                        }
                    }
                }
            }
            if (destinoElegido == null && e.inicio != null) {
                Text(
                    "Sugeridos para tus ${formatoKm(e.distanciaKm)} km (${e.modalidad.etiqueta.lowercase()})",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Espacio.m, bottom = Espacio.s),
                )
                when {
                    e.cargandoSugerencias -> EstadoCargando("Buscando parques y plazas a esa distancia…")
                    e.sugerencias.isEmpty() -> Text(
                        "No encontramos parques ni plazas a esa distancia. Prueba con otra distancia o elige en el mapa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Runway.TextoSecundario,
                    )
                    else -> {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                            items(e.sugerencias, key = { idSugerencia(it) }) { s -> ChipSugerencia(s) { vm.elegirSugerencia(s) } }
                        }
                        androidx.compose.material3.TextButton(onClick = {
                            vm.abrirSelector(ObjetivoPunto.Destino)
                            onElegirPunto(ObjetivoPunto.Destino)
                        }) { Text("Ver sugerencias en el mapa", color = Runway.MoradoOscuro) }
                    }
                }
            }

            // --- Distancia objetivo
            Seccion("Distancia")
            val conDestino = e.distanciaConDestinoKm
            if (conDestino != null) {
                // Con destino, la distancia la marca el camino hasta él (y de vuelta si es circular)
                TarjetaRunway(Modifier.fillMaxWidth(), color = Runway.LavandaClaro, relleno = Espacio.l) {
                    Text("≈ ${formatoKm(conDestino)} km", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (e.modalidad == Modalidad.Lineal) "Distancia aproximada hasta tu destino."
                        else "Distancia aproximada de ida y vuelta pasando por tu destino.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Runway.TextoSecundario,
                    )
                }
            } else {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BotonCircular(Icons.Outlined.Remove, "Menos distancia", onClick = { vm.onDistancia(e.distanciaKm - 0.5) })
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(formatoKm(e.distanciaKm), style = MaterialTheme.typography.displayMedium)
                    Text(" km", style = MaterialTheme.typography.titleMedium, color = Runway.TextoSecundario, modifier = Modifier.padding(bottom = 6.dp))
                }
                BotonCircular(Icons.Outlined.Add, "Más distancia", onClick = { vm.onDistancia(e.distanciaKm + 0.5) })
            }
            Spacer(Modifier.height(Espacio.m))
            GrupoOpciones(
                DISTANCIAS_RAPIDAS,
                DISTANCIAS_RAPIDAS.firstOrNull { it == e.distanciaKm },
                { "${it.toInt()} km" },
                onSeleccion = vm::onDistancia,
            )

            }

            // --- Modalidad
            Seccion("Modalidad")
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                Modalidad.entries.forEach { m ->
                    TarjetaOpcion(m.etiqueta, m.descripcion, e.modalidad == m, onClick = { vm.onModalidad(m) }, modifier = Modifier.weight(1f))
                }
            }

            // --- Nivel y desnivel (vienen del perfil, se pueden ajustar para esta salida)
            Seccion("Nivel")
            GrupoOpciones(Nivel.entries, e.nivel, { it.etiqueta }, onSeleccion = vm::onNivel)
            Seccion("Desnivel")
            GrupoOpciones(PreferenciaDesnivel.entries, e.desnivel, { it.etiqueta }, onSeleccion = vm::onDesnivel)
            Text(
                e.desnivel.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = Runway.TextoSecundario,
                modifier = Modifier.padding(top = Espacio.s, bottom = Espacio.l),
            )
        }

        // Acción principal
        Column(Modifier.padding(horizontal = Espacio.margen, vertical = Espacio.m)) {
            if (e.inicio == null && !e.ubicandome) {
                Text(
                    "Elige un punto de inicio para buscar rutas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Runway.TextoSecundario,
                    modifier = Modifier.padding(bottom = Espacio.s),
                )
            }
            BotonRunway(
                "Buscar rutas",
                onClick = {
                    vm.buscarRutas()
                    onBuscar()
                },
                icono = Icons.Outlined.Search,
                habilitado = e.puedeBuscar,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TarjetaDestino(nombre: String, detalle: String, onCambiar: () -> Unit, onQuitar: () -> Unit) {
    TarjetaRunway(Modifier.fillMaxWidth(), relleno = Espacio.l) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Espacio.radioPequeno), color = Runway.Texto, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Flag, null, tint = Runway.Lima, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Text(nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.material3.TextButton(onClick = onCambiar) { Text("Cambiar", color = Runway.Morado) }
            BotonCircular(Icons.Outlined.Close, "Quitar destino", onClick = onQuitar, tamano = 36.dp)
        }
    }
}

@Composable
private fun Seccion(titulo: String) {
    Text(
        titulo,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Espacio.xl, bottom = Espacio.m),
    )
}

@Composable
private fun TarjetaInicio(nombre: String, detalle: String, onCambiar: (() -> Unit)?) {
    TarjetaRunway(Modifier.fillMaxWidth(), relleno = Espacio.l) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(Espacio.radioPequeno), color = Runway.LimaClaro, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.LocationOn, null, tint = Runway.Texto, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Text(nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detalle.isNotBlank()) {
                    Text(detalle, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (onCambiar != null) {
                androidx.compose.material3.TextButton(onClick = onCambiar) { Text("Cambiar", color = Runway.Morado) }
            }
        }
    }
}
