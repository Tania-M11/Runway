package com.udenar.runway.ui.configuracion

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.PreferenciaDesnivel
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.CampoTexto
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaOpcion
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import com.udenar.runway.ui.ubicacion.rememberPedirPermisoUbicacion

/** Configuración inicial del corredor (especificación 5.1), en 4 pasos cortos. */
@Composable
fun ConfiguracionInicialScreen(vm: ConfiguracionViewModel = viewModel(factory = ConfiguracionViewModel.Factory)) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    // Sea cual sea la respuesta al permiso, se termina: sin permiso se explora el mapa a mano
    val pedirPermiso = rememberPedirPermisoUbicacion { vm.terminar() }

    BackHandler(enabled = estado.paso.ordinal > 0) { vm.anterior() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Espacio.margen),
    ) {
        // Encabezado: atrás + progreso por pasos
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = Espacio.l).height(48.dp),
        ) {
            if (estado.paso.ordinal > 0) {
                BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = { vm.anterior() })
                Spacer(Modifier.size(Espacio.m))
            }
            BarraPasos(actual = estado.paso.ordinal, total = PasoConfiguracion.entries.size, modifier = Modifier.weight(1f))
        }

        AnimatedContent(
            targetState = estado.paso,
            transitionSpec = {
                val adelante = targetState.ordinal > initialState.ordinal
                (slideInHorizontally { if (adelante) it / 4 else -it / 4 } + fadeIn()) togetherWith fadeOut()
            },
            label = "paso",
            modifier = Modifier.weight(1f),
        ) { paso ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Espacio.xxl),
                verticalArrangement = Arrangement.spacedBy(Espacio.m),
            ) {
                when (paso) {
                    PasoConfiguracion.Nombre -> {
                        Encabezado(
                            Icons.AutoMirrored.Outlined.DirectionsRun,
                            "Te damos la bienvenida a RUNWAY",
                            "Planifica recorridos a tu medida, registra tus carreras y descubre nuevos caminos. ¿Cómo te llamamos?",
                        )
                        CampoTexto(
                            valor = estado.nombre,
                            onValor = vm::onNombre,
                            etiqueta = "Nombre o alias",
                            ayuda = "${estado.nombre.length}/$MAX_NOMBRE",
                            onListo = {
                                foco.clearFocus()
                                vm.siguiente()
                            },
                        )
                    }
                    PasoConfiguracion.Nivel -> {
                        Encabezado(null, "¿Cuál es tu nivel?", "Así te proponemos distancias alcanzables.")
                        Nivel.entries.forEach { n ->
                            TarjetaOpcion(n.etiqueta, n.descripcion, estado.nivel == n, onClick = { vm.onNivel(n) })
                        }
                    }
                    PasoConfiguracion.Desnivel -> {
                        Encabezado(null, "¿Qué terreno prefieres?", "Lo usamos para elegir entre rutas planas o con subidas.")
                        PreferenciaDesnivel.entries.forEach { d ->
                            TarjetaOpcion(d.etiqueta, d.descripcion, estado.desnivel == d, onClick = { vm.onDesnivel(d) })
                        }
                    }
                    PasoConfiguracion.Ubicacion -> {
                        Encabezado(
                            Icons.Outlined.MyLocation,
                            "Usa tu ubicación",
                            "RUNWAY la necesita para mostrarte dónde estás, proponerte rutas que empiecen cerca de ti " +
                                "y registrar tus entrenamientos con GPS.\n\nSi prefieres no darla, igual puedes explorar " +
                                "el mapa y elegir el punto de inicio a mano. Puedes cambiarlo después.",
                        )
                    }
                }
            }
        }

        // Acciones abajo, siempre a la vista
        Column(Modifier.padding(vertical = Espacio.l), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            if (estado.paso == PasoConfiguracion.Ubicacion) {
                BotonRunway("Permitir ubicación", onClick = pedirPermiso, modifier = Modifier.fillMaxWidth())
                BotonRunway("Ahora no", onClick = vm::terminar, estilo = EstiloBoton.Contorno, modifier = Modifier.fillMaxWidth())
            } else {
                BotonRunway(
                    "Continuar",
                    onClick = {
                        foco.clearFocus()
                        vm.siguiente()
                    },
                    habilitado = estado.puedeContinuar,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Encabezado(icono: ImageVector?, titulo: String, texto: String) {
    if (icono != null) {
        // Espacio reservado para la ilustración de bienvenida (la aporta el equipo)
        Surface(shape = CircleShape, color = Runway.Morado, modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(icono, null, tint = Runway.Lima, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.height(Espacio.s))
    }
    Text(titulo, style = MaterialTheme.typography.headlineMedium)
    Text(texto, style = MaterialTheme.typography.bodyLarge, color = Runway.TextoSecundario)
    Spacer(Modifier.height(Espacio.s))
}

@Composable
private fun BarraPasos(actual: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Surface(
                shape = CircleShape,
                color = when {
                    i < actual -> Runway.Morado
                    i == actual -> Runway.Lima
                    else -> Runway.Borde
                },
                modifier = Modifier.weight(1f).height(6.dp),
            ) {}
        }
    }
}
