package com.udenar.runway.ui.mapa

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.PosicionCamara
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback

/**
 * Estilo cartográfico de OpenFreeMap: teselas vectoriales con datos de OpenStreetMap,
 * gratuitas y sin clave de API. El estilo ya incluye la atribución © OpenStreetMap.
 */
const val ESTILO_MAPA = "https://tiles.openfreemap.org/styles/liberty"

/** Órdenes que la capa de presentación (ViewModel) envía al mapa. */
sealed interface ComandoMapa {
    data class MoverCamara(val destino: Coordenada, val zoom: Double) : ComandoMapa
    /** Ajusta la cámara para que se vean todos los puntos (p. ej. una ruta completa). */
    data class Encuadrar(val puntos: List<Coordenada>, val margenPx: Int = 120) : ComandoMapa
}

/** Amigo que está corriendo ahora (se dibuja con su avatar). */
data class MarcadorAmigo(val id: String, val nombre: String, val avatar: String, val coordenada: Coordenada)

/** Una línea a dibujar sobre el mapa (alternativa de ruta o trazado GPS). */
data class TrazoRuta(
    val id: String,
    val puntos: List<Coordenada>,
    val colorHex: String,
    val seleccionada: Boolean,
)

/**
 * Integración del mapa en Compose (mecanismos 2, 3 y 4 del tema de clase).
 *
 * - **Contenedor (2):** `MapView` es una vista clásica de Android; se inserta en Compose
 *   con `AndroidView` y se le reenvía el ciclo de vida (onStart, onResume, onPause…),
 *   igual que haría una Activity con un `MapView` de Google Maps.
 * - **Operaciones automáticas (3):** al darle un estilo, MapLibre se conecta solo al servidor
 *   de teselas, las descarga y guarda en caché, las dibuja con la GPU y maneja los gestos
 *   (arrastrar, pellizcar, girar, inclinar). Nada de eso se programa aquí.
 * - **Control lógico y eventos (4):** `getMapAsync(OnMapReadyCallback)` entrega el
 *   controlador del motor; con él se crea el [ControladorMapa], que personaliza el estado
 *   inicial, añade marcadores, traza rutas y gestiona superposiciones.
 *
 * Este composable no decide nada: recibe estado y órdenes, y avisa eventos.
 */
@Composable
fun MapaRunway(
    camaraInicial: PosicionCamara,
    modifier: Modifier = Modifier,
    puntoSeleccionado: Coordenada? = null,
    destinoRuta: Coordenada? = null,
    miPosicion: Coordenada? = null,
    rutas: List<TrazoRuta> = emptyList(),
    finRuta: Coordenada? = null,
    amigos: List<MarcadorAmigo> = emptyList(),
    lugares: List<MarcadorLugar> = emptyList(),
    superposiciones: List<Superposicion> = emptyList(),
    comandos: Flow<ComandoMapa> = emptyFlow(),
    intentoCarga: Int = 0,
    onEstiloCargado: () -> Unit = {},
    onErrorCarga: (String?) -> Unit = {},
    onCamaraQuieta: (PosicionCamara) -> Unit = {},
    onToque: (Coordenada) -> Unit = {},
    onToqueRuta: ((String) -> Unit)? = null,
    onToqueLugar: ((String) -> Unit)? = null,
    /** El usuario arrastró el mapa con el dedo (p. ej. para dejar de seguir su posición). */
    onArrastre: () -> Unit = {},
    /** Recuadro visible cada vez que la cámara se detiene (para descargar "esta zona"). */
    onLimitesVisibles: (Coordenada, Coordenada) -> Unit = { _, _ -> },
    margenSuperiorBrujula: Int = 0,
) {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle

    val camara by rememberUpdatedState(camaraInicial)
    val alCargar by rememberUpdatedState(onEstiloCargado)
    val alFallar by rememberUpdatedState(onErrorCarga)
    val alMoverse by rememberUpdatedState(onCamaraQuieta)
    val alTocar by rememberUpdatedState(onToque)
    val alTocarRuta by rememberUpdatedState(onToqueRuta)
    val alTocarLugar by rememberUpdatedState(onToqueLugar)
    val alArrastrar by rememberUpdatedState(onArrastre)
    val alVerLimites by rememberUpdatedState(onLimitesVisibles)

    // (2) Contenedor de interfaz: la vista nativa del mapa
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var controlador by remember { mutableStateOf<ControladorMapa?>(null) }
    var estiloListo by remember { mutableStateOf(false) }

    // Reenvío del ciclo de vida de la pantalla al MapView
    DisposableEffect(ciclo, mapView) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            if (ciclo.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (ciclo.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    // (4) OnMapReadyCallback: el motor entrega su controlador y se crea el ControladorMapa
    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { mensaje -> alFallar(mensaje) }
        mapView.getMapAsync(OnMapReadyCallback { m ->
            val c = ControladorMapa(m, context)
            c.configurarEstadoInicial(camara, margenSuperiorBrujula)
            c.escucharEventos(
                onToque = { alTocar(it) },
                onToqueRuta = { alTocarRuta },
                onToqueLugar = { alTocarLugar },
                onCamaraQuieta = { alMoverse(it) },
                onArrastre = { alArrastrar() },
                onLimitesVisibles = { sw, ne -> alVerLimites(sw, ne) },
            )
            controlador = c
        })
    }

    // (3) Al fijar el estilo, el motor se conecta al servidor y renderiza solo.
    // Se repite cuando el usuario pulsa "Reintentar" (cambia intentoCarga).
    LaunchedEffect(controlador, intentoCarga) {
        val c = controlador ?: return@LaunchedEffect
        estiloListo = false
        c.cargarEstilo(ESTILO_MAPA) {
            estiloListo = true
            alCargar()
        }
    }

    // El estado de la pantalla se refleja en el mapa a través del controlador
    val c = controlador.takeIf { estiloListo }
    LaunchedEffect(c, puntoSeleccionado) { c?.mostrarMarcador(TipoMarcador.Inicio, puntoSeleccionado) }
    LaunchedEffect(c, destinoRuta) { c?.mostrarMarcador(TipoMarcador.Destino, destinoRuta) }
    LaunchedEffect(c, finRuta) { c?.mostrarMarcador(TipoMarcador.Fin, finRuta) }
    LaunchedEffect(c, miPosicion) { c?.mostrarMarcador(TipoMarcador.MiPosicion, miPosicion) }
    LaunchedEffect(c, amigos) { c?.mostrarAmigos(amigos) }
    LaunchedEffect(c, lugares) { c?.mostrarLugares(lugares) }
    LaunchedEffect(c, rutas) { c?.trazarRutas(rutas) }
    LaunchedEffect(c, superposiciones) { c?.mostrarSuperposiciones(superposiciones) }

    // Órdenes del ViewModel → controlador (esperan a que el estilo esté listo)
    LaunchedEffect(c) {
        val listo = c ?: return@LaunchedEffect
        comandos.collect { listo.ejecutar(it) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
