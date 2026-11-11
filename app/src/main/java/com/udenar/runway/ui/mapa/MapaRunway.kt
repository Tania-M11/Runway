package com.udenar.runway.ui.mapa

import android.graphics.Color
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Estilo cartográfico de OpenFreeMap: teselas vectoriales con datos de OpenStreetMap,
 * gratuitas y sin clave de API. El estilo ya incluye la atribución © OpenStreetMap.
 */
const val ESTILO_MAPA = "https://tiles.openfreemap.org/styles/liberty"

/** Órdenes que la capa de presentación (ViewModel) envía al mapa. */
sealed interface ComandoMapa {
    data class MoverCamara(val destino: Coordenada, val zoom: Double) : ComandoMapa
}

private const val FUENTE_PUNTO = "punto-seleccionado"
private const val CAPA_PUNTO = "punto-seleccionado-capa"

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
 *   controlador `MapLibreMap` (equivalente a `GoogleMap`). Con él se mueve la cámara,
 *   se agregan capas y se escuchan toques y movimientos, que se suben al ViewModel.
 *
 * Este composable no decide nada: recibe estado y órdenes, y avisa eventos.
 */
@Composable
fun MapaRunway(
    camaraInicial: PosicionCamara,
    puntoSeleccionado: Coordenada?,
    comandos: Flow<ComandoMapa>,
    intentoCarga: Int,
    onEstiloCargado: () -> Unit,
    onErrorCarga: (String?) -> Unit,
    onCamaraQuieta: (PosicionCamara) -> Unit,
    onToque: (Coordenada) -> Unit,
    modifier: Modifier = Modifier,
    margenSuperiorBrujula: Int = 0,
) {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle

    val camara by rememberUpdatedState(camaraInicial)
    val alCargar by rememberUpdatedState(onEstiloCargado)
    val alFallar by rememberUpdatedState(onErrorCarga)
    val alMoverse by rememberUpdatedState(onCamaraQuieta)
    val alTocar by rememberUpdatedState(onToque)

    // (2) Contenedor de interfaz: la vista nativa del mapa
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var estilo by remember { mutableStateOf<Style?>(null) }

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

    // (4) OnMapReadyCallback: el motor avisa cuando el controlador está listo
    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { mensaje -> alFallar(mensaje) }
        mapView.getMapAsync(OnMapReadyCallback { m ->
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(camara.centro.lat, camara.centro.lon))
                .zoom(camara.zoom)
                .build()
            m.uiSettings.setCompassMargins(0, margenSuperiorBrujula, 32, 0)
            m.addOnCameraIdleListener {
                m.cameraPosition.target?.let {
                    alMoverse(PosicionCamara(Coordenada(it.latitude, it.longitude), m.cameraPosition.zoom))
                }
            }
            m.addOnMapClickListener { punto ->
                alTocar(Coordenada(punto.latitude, punto.longitude))
                true
            }
            mapa = m
        })
    }

    // (3) Al fijar el estilo, el motor se conecta al servidor y renderiza solo.
    // Se repite cuando el usuario pulsa "Reintentar" (cambia intentoCarga).
    LaunchedEffect(mapa, intentoCarga) {
        val m = mapa ?: return@LaunchedEffect
        estilo = null
        m.setStyle(Style.Builder().fromUri(ESTILO_MAPA)) { s ->
            prepararCapas(s)
            estilo = s
            alCargar()
        }
    }

    // Capa del punto seleccionado (GeoJSON + círculo morado)
    LaunchedEffect(estilo, puntoSeleccionado) {
        val fuente = estilo?.getSourceAs<GeoJsonSource>(FUENTE_PUNTO) ?: return@LaunchedEffect
        if (puntoSeleccionado == null) {
            fuente.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            fuente.setGeoJson(Feature.fromGeometry(Point.fromLngLat(puntoSeleccionado.lon, puntoSeleccionado.lat)))
        }
    }

    // Órdenes del ViewModel → controlador del mapa
    LaunchedEffect(mapa) {
        val m = mapa ?: return@LaunchedEffect
        comandos.collect { comando ->
            when (comando) {
                is ComandoMapa.MoverCamara -> m.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(comando.destino.lat, comando.destino.lon), comando.zoom),
                    900,
                )
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun prepararCapas(estilo: Style) {
    estilo.addSource(GeoJsonSource(FUENTE_PUNTO))
    estilo.addLayer(
        CircleLayer(CAPA_PUNTO, FUENTE_PUNTO).withProperties(
            circleRadius(9f),
            circleColor(Color.parseColor("#7668E8")),
            circleStrokeWidth(3f),
            circleStrokeColor(Color.WHITE),
        ),
    )
}
