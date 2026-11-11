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
fun PlanificarScreen(vm: PlanificadorViewModel, onVolver: () -> Unit, onBuscar: () -> Unit) {
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
            GrupoOpciones(origenes, e.origen, { it.etiqueta }, onSeleccion = vm::elegirOrigen)
            Spacer(Modifier.height(Espacio.m))
            when {
                e.origen == OrigenInicio.Buscar && e.inicio == null -> {
                    BarraBusqueda(e.consulta, vm::onConsulta, "Ciudad, barrio o parque")
                    when {
                        e.buscandoLugar -> EstadoCargando("Buscando lugares…")
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
                    onCambiar = if (e.origen == OrigenInicio.Buscar) ({ vm.elegirOrigen(OrigenInicio.Buscar) }) else null,
                )
            }

            // --- Distancia objetivo
            Seccion("Distancia")
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
