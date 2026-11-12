package com.udenar.runway.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.udenar.runway.domain.AVATARES
import com.udenar.runway.ui.components.recursoAvatar
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import android.graphics.RectF
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
import com.udenar.runway.domain.distanciaKm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineSortKey
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

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

private const val FUENTE_PUNTO = "punto-seleccionado"
private const val CAPA_PUNTO = "punto-seleccionado-capa"
private const val FUENTE_YO = "mi-ubicacion"
private const val CAPA_YO_HALO = "mi-ubicacion-halo"
private const val CAPA_YO = "mi-ubicacion-punto"
private const val FUENTE_RUTAS = "rutas"
private const val CAPA_RUTAS_BORDE = "rutas-borde"
private const val CAPA_RUTAS = "rutas-linea"
private const val FUENTE_FIN = "fin-ruta"
private const val FUENTE_AMIGOS = "amigos-en-vivo"
private const val CAPA_AMIGOS = "amigos-en-vivo-capa"
private const val CAPA_FIN = "fin-ruta-capa"

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
    modifier: Modifier = Modifier,
    puntoSeleccionado: Coordenada? = null,
    miPosicion: Coordenada? = null,
    rutas: List<TrazoRuta> = emptyList(),
    finRuta: Coordenada? = null,
    amigos: List<MarcadorAmigo> = emptyList(),
    comandos: Flow<ComandoMapa> = emptyFlow(),
    intentoCarga: Int = 0,
    onEstiloCargado: () -> Unit = {},
    onErrorCarga: (String?) -> Unit = {},
    onCamaraQuieta: (PosicionCamara) -> Unit = {},
    onToque: (Coordenada) -> Unit = {},
    onToqueRuta: ((String) -> Unit)? = null,
    /** El usuario arrastró el mapa con el dedo (p. ej. para dejar de seguir su posición). */
    onArrastre: () -> Unit = {},
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
    val alArrastrar by rememberUpdatedState(onArrastre)

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
            m.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                override fun onMoveBegin(detector: MoveGestureDetector) = alArrastrar()
                override fun onMove(detector: MoveGestureDetector) = Unit
                override fun onMoveEnd(detector: MoveGestureDetector) = Unit
            })
            m.addOnMapClickListener { punto ->
                // ¿Se tocó una ruta? Se consultan las líneas dibujadas bajo el dedo (±24 px)
                val tocarRuta = alTocarRuta
                if (tocarRuta != null) {
                    val p = m.projection.toScreenLocation(punto)
                    val zona = RectF(p.x - 24, p.y - 24, p.x + 24, p.y + 24)
                    val id = m.queryRenderedFeatures(zona, CAPA_RUTAS).firstNotNullOfOrNull { it.getStringProperty("id") }
                    if (id != null) {
                        tocarRuta(id)
                        return@addOnMapClickListener true
                    }
                }
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
            prepararCapas(s, context)
            estilo = s
            alCargar()
        }
    }

    LaunchedEffect(estilo, puntoSeleccionado) {
        estilo?.getSourceAs<GeoJsonSource>(FUENTE_PUNTO)?.setGeoJson(geojsonDe(puntoSeleccionado))
    }
    LaunchedEffect(estilo, finRuta) {
        estilo?.getSourceAs<GeoJsonSource>(FUENTE_FIN)?.setGeoJson(geojsonDe(finRuta))
    }
    LaunchedEffect(estilo, miPosicion) {
        estilo?.getSourceAs<GeoJsonSource>(FUENTE_YO)?.setGeoJson(geojsonDe(miPosicion))
    }
    LaunchedEffect(estilo, amigos) {
        estilo?.getSourceAs<GeoJsonSource>(FUENTE_AMIGOS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                amigos.map { a ->
                    Feature.fromGeometry(Point.fromLngLat(a.coordenada.lon, a.coordenada.lat)).apply {
                        addStringProperty("id", a.id)
                        addStringProperty("nombre", a.nombre)
                        addStringProperty("avatar", a.avatar)
                    }
                },
            ),
        )
    }
    LaunchedEffect(estilo, rutas) {
        estilo?.getSourceAs<GeoJsonSource>(FUENTE_RUTAS)?.setGeoJson(geojsonDeRutas(rutas))
    }

    // Órdenes del ViewModel → controlador del mapa (esperan a que el estilo esté listo)
    LaunchedEffect(mapa, estilo != null) {
        val m = mapa ?: return@LaunchedEffect
        if (estilo == null) return@LaunchedEffect
        comandos.collect { comando -> ejecutar(m, comando) }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun ejecutar(m: MapLibreMap, comando: ComandoMapa) {
    when (comando) {
        is ComandoMapa.MoverCamara -> m.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(comando.destino.lat, comando.destino.lon), comando.zoom),
            900,
        )
        is ComandoMapa.Encuadrar -> {
            val distintos = comando.puntos.distinct()
            if (distintos.size < 2) {
                distintos.firstOrNull()?.let { m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 15.0), 700) }
                return
            }
            val limites = LatLngBounds.Builder().includes(distintos.map { LatLng(it.lat, it.lon) }).build()
            val diagonalKm = distanciaKm(
                Coordenada(limites.latitudeSouth, limites.longitudeWest),
                Coordenada(limites.latitudeNorth, limites.longitudeEast),
            )
            if (diagonalKm < 0.2) {
                // Recorridos de pocos metros: encuadrarlos acercaría el mapa al máximo
                val centro = limites.center
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(centro, 16.5), 700)
            } else {
                m.animateCamera(CameraUpdateFactory.newLatLngBounds(limites, comando.margenPx), 800)
            }
        }
    }
}

/** Avatar recortado en círculo con anillo morado y blanco, para usarlo como ícono del mapa. */
private fun avatarCircular(context: Context, id: String, tamano: Int): Bitmap {
    val original = BitmapFactory.decodeResource(context.resources, recursoAvatar(id))
    val salida = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.ARGB_8888)
    val lienzo = Canvas(salida)
    val centro = tamano / 2f
    val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    pincel.color = Color.parseColor("#7668E8")
    lienzo.drawCircle(centro, centro, centro, pincel)
    pincel.color = Color.WHITE
    lienzo.drawCircle(centro, centro, centro * 0.88f, pincel)
    val recorte = Path().apply { addCircle(centro, centro, centro * 0.80f, Path.Direction.CW) }
    lienzo.save()
    lienzo.clipPath(recorte)
    val r = centro * 0.80f
    lienzo.drawBitmap(original, null, RectF(centro - r, centro - r, centro + r, centro + r), Paint(Paint.FILTER_BITMAP_FLAG))
    lienzo.restore()
    original.recycle()
    return salida
}

private fun geojsonDe(c: Coordenada?): FeatureCollection = FeatureCollection.fromFeatures(
    listOfNotNull(c?.let { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }),
)

/** La seleccionada va al final y con mayor orden para dibujarse encima de las demás. */
private fun geojsonDeRutas(rutas: List<TrazoRuta>): FeatureCollection = FeatureCollection.fromFeatures(
    rutas.sortedBy { it.seleccionada }.map { r ->
        Feature.fromGeometry(LineString.fromLngLats(r.puntos.map { Point.fromLngLat(it.lon, it.lat) })).apply {
            addStringProperty("id", r.id)
            addStringProperty("color", r.colorHex)
            addBooleanProperty("sel", r.seleccionada)
            addNumberProperty("orden", if (r.seleccionada) 1 else 0)
        }
    },
)

/** Capas propias sobre el mapa base, de abajo hacia arriba: rutas, usuario, fin y punto de inicio. */
private fun prepararCapas(estilo: Style, context: Context) {
    val seleccionada = Expression.toBool(Expression.get("sel"))
    estilo.addSource(GeoJsonSource(FUENTE_RUTAS))
    // Borde blanco solo en la ruta seleccionada, para resaltarla (especificación 5.5)
    estilo.addLayer(
        LineLayer(CAPA_RUTAS_BORDE, FUENTE_RUTAS).withProperties(
            lineColor(Color.WHITE),
            lineWidth(11f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(seleccionada),
    )
    estilo.addLayer(
        LineLayer(CAPA_RUTAS, FUENTE_RUTAS).withProperties(
            lineColor(Expression.toColor(Expression.get("color"))),
            lineWidth(Expression.switchCase(seleccionada, Expression.literal(6f), Expression.literal(4f))),
            lineOpacity(Expression.switchCase(seleccionada, Expression.literal(1f), Expression.literal(0.5f))),
            lineSortKey(Expression.get("orden")),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )

    estilo.addSource(GeoJsonSource(FUENTE_YO))
    estilo.addLayer(
        CircleLayer(CAPA_YO_HALO, FUENTE_YO).withProperties(
            circleRadius(20f),
            circleColor(Color.parseColor("#7668E8")),
            circleOpacity(0.18f),
        ),
    )
    estilo.addLayer(
        CircleLayer(CAPA_YO, FUENTE_YO).withProperties(
            circleRadius(8f),
            circleColor(Color.parseColor("#5144B8")),
            circleStrokeWidth(3f),
            circleStrokeColor(Color.WHITE),
        ),
    )

    // Final de una ruta lineal: punto oscuro con borde blanco
    estilo.addSource(GeoJsonSource(FUENTE_FIN))
    estilo.addLayer(
        CircleLayer(CAPA_FIN, FUENTE_FIN).withProperties(
            circleRadius(9f),
            circleColor(Color.parseColor("#172033")),
            circleStrokeWidth(4f),
            circleStrokeColor(Color.WHITE),
        ),
    )

    // Amigos corriendo: su avatar en círculo con borde morado y el nombre debajo (capa de símbolos)
    val tamano = (44 * context.resources.displayMetrics.density).toInt()
    AVATARES.forEach { id -> estilo.addImage(id, avatarCircular(context, id, tamano)) }
    estilo.addSource(GeoJsonSource(FUENTE_AMIGOS))
    estilo.addLayer(
        SymbolLayer(CAPA_AMIGOS, FUENTE_AMIGOS).withProperties(
            iconImage(Expression.get("avatar")),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
            textField(Expression.get("nombre")),
            textFont(arrayOf("Noto Sans Regular")), // fuente incluida en el estilo de OpenFreeMap
            textSize(12f),
            textAnchor(Property.TEXT_ANCHOR_TOP),
            textOffset(arrayOf(0f, 1.9f)),
            textColor(Color.parseColor("#172033")),
            textHaloColor(Color.WHITE),
            textHaloWidth(1.5f),
            textAllowOverlap(true),
        ),
    )

    // Punto elegido / inicio de ruta: lima con borde oscuro
    estilo.addSource(GeoJsonSource(FUENTE_PUNTO))
    estilo.addLayer(
        CircleLayer(CAPA_PUNTO, FUENTE_PUNTO).withProperties(
            circleRadius(10f),
            circleColor(Color.parseColor("#D5F36A")),
            circleStrokeWidth(4f),
            circleStrokeColor(Color.parseColor("#172033")),
        ),
    )
}
