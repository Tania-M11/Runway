package com.udenar.runway.ui.rutas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.RunwayApp
import com.udenar.runway.ui.offline.TarjetaMapaOffline
import com.udenar.runway.ui.offline.comoSuperposiciones
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.RutaCandidata
import com.udenar.runway.domain.formatoDuracion
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.domain.formatoMetros
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.TarjetaMetrica
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.mapa.ComandoMapa
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.mapa.TrazoRuta
import com.udenar.runway.ui.planificar.EtiquetaExigencia
import com.udenar.runway.ui.planificar.PerfilElevacion
import com.udenar.runway.ui.planificar.aColor
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import kotlinx.coroutines.flow.Flow

/**
 * Detalle de una ruta (especificación 5.6): mapa con el trazado, métricas, perfil de
 * elevación e información. Lo usan el planificador y Mis rutas; cada uno pone sus acciones.
 */
@Composable
fun PantallaDetalleRuta(
    ruta: RutaCandidata,
    colorHex: String,
    inicioNombre: String,
    finNombre: String?,
    destino: com.udenar.runway.domain.Coordenada? = null,
    comandos: Flow<ComandoMapa>,
    onMapaListo: () -> Unit,
    onVolver: () -> Unit,
    descripcionVolver: String,
    encabezado: @Composable () -> Unit = {},
    acciones: @Composable ColumnScope.() -> Unit,
) {
    val camara = remember(ruta.id) { PosicionCamara(ruta.inicio, 14.0) }
    val zonas by (LocalContext.current.applicationContext as RunwayApp).contenedor.mapasOffline.zonas.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                MapaRunway(
                    camaraInicial = camara,
                    puntoSeleccionado = ruta.inicio,
                    finRuta = ruta.fin.takeIf { ruta.modalidad == Modalidad.Lineal && destino == null },
                    destinoRuta = destino,
                    // Zonas descargadas: rectángulo punteado (superposición) sobre el mapa
                    superposiciones = zonas.comoSuperposiciones(),
                    rutas = listOf(TrazoRuta(ruta.id, ruta.puntos, colorHex, seleccionada = true)),
                    comandos = comandos,
                    onEstiloCargado = onMapaListo,
                    modifier = Modifier.fillMaxSize(),
                )
                BotonCircular(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    descripcionVolver,
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
                    Spacer(Modifier.width(Espacio.s))
                    EtiquetaExigencia(ruta.exigencia)
                }
                encabezado()

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
                        PerfilElevacion(ruta.elevaciones, colorHex.aColor())
                    }
                }

                Spacer(Modifier.height(Espacio.l))
                TarjetaRunway(Modifier.fillMaxWidth()) {
                    FilaInfo("Inicio", inicioNombre)
                    HorizontalDivider(color = Runway.Borde)
                    FilaInfo(
                        "Final",
                        if (ruta.modalidad == Modalidad.Circular) "El mismo punto de inicio" else finNombre ?: "Buscando…",
                    )
                    if (ruta.calles.isNotEmpty()) {
                        HorizontalDivider(color = Runway.Borde)
                        FilaInfo("Vías principales", ruta.calles.joinToString(", "))
                    }
                }

                Spacer(Modifier.height(Espacio.l))
                TarjetaMapaOffline(ruta.puntos, nombre = ruta.nombre)

                Spacer(Modifier.height(Espacio.xl))
                acciones()
                Spacer(Modifier.height(Espacio.xl))
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
