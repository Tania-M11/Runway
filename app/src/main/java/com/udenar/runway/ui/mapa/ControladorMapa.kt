package com.udenar.runway.ui.mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.udenar.runway.domain.AVATARES
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.destino
import com.udenar.runway.domain.distanciaKm
import com.udenar.runway.ui.components.recursoAvatar
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineSortKey
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Marcadores fijos que maneja el controlador (cada uno con su propia capa y estilo). */
enum class TipoMarcador { Inicio, Destino, Fin, MiPosicion }

/** Lugar sugerido u otra anotación que se puede tocar en el mapa. */
data class MarcadorLugar(val id: String, val nombre: String, val coordenada: Coordenada)

/** Superposiciones (overlays) dibujadas sobre el mapa base. */
sealed interface Superposicion {
    /** Círculo semitransparente, p. ej. el alcance de la distancia objetivo. */
    data class Circulo(val centro: Coordenada, val radioKm: Double) : Superposicion
    /** Rectángulo, p. ej. el área descargada para usar sin conexión. */
    data class Rectangulo(val suroeste: Coordenada, val noreste: Coordenada) : Superposicion
}

/**
 * **MapController de RUNWAY** (mecanismo 4 de la guía de recursos).
 *
 * Envuelve al controlador del motor (`MapLibreMap`, equivalente a `GoogleMap`/`MapController`)
 * y reúne en un solo lugar lo que la guía describe:
 *
 * 1. **Personalizar el estado inicial del mapa:** [configurarEstadoInicial].
 * 2. **Añadir marcadores:** [mostrarMarcador], [mostrarLugares], [mostrarAmigos].
 * 3. **Trazar rutas:** [trazarRutas].
 * 4. **Gestionar superposiciones (overlays) o anotaciones:** [mostrarSuperposiciones].
 *
 * Además ejecuta las órdenes de cámara ([ejecutar]) y registra los eventos del usuario
 * ([escucharEventos]). Todo se dibuja con fuentes GeoJSON + capas del estilo: así el motor
 * las renderiza en la GPU junto con el mapa base.
 */
class ControladorMapa(private val mapa: MapLibreMap, private val context: Context) {

    var estilo: Style? = null
        private set

    // ------------------------------------------------------------------ 1. Estado inicial

    fun configurarEstadoInicial(camara: PosicionCamara, margenSuperiorBrujula: Int) {
        mapa.cameraPosition = CameraPosition.Builder()
            .target(LatLng(camara.centro.lat, camara.centro.lon))
            .zoom(camara.zoom)
            .build()
        mapa.setMinZoomPreference(3.0)
        mapa.setMaxZoomPreference(19.0)
        mapa.uiSettings.setCompassMargins(0, margenSuperiorBrujula, 32, 0)
    }

    /** Carga el estilo cartográfico y prepara las capas propias encima del mapa base. */
    fun cargarEstilo(uri: String, alTerminar: () -> Unit) {
        estilo = null
        mapa.setStyle(Style.Builder().fromUri(uri)) { s ->
            prepararCapas(s)
            estilo = s
            alTerminar()
        }
    }

    // ------------------------------------------------------------------ 2. Marcadores

    fun mostrarMarcador(tipo: TipoMarcador, c: Coordenada?) {
        val fuente = when (tipo) {
            TipoMarcador.Inicio -> FUENTE_INICIO
            TipoMarcador.Destino -> FUENTE_DESTINO
            TipoMarcador.Fin -> FUENTE_FIN
            TipoMarcador.MiPosicion -> FUENTE_YO
        }
        fuente(fuente)?.setGeoJson(puntos(listOfNotNull(c)))
    }

    /** Lugares sugeridos u otras anotaciones con nombre (se pueden tocar). */
    fun mostrarLugares(lugares: List<MarcadorLugar>) {
        fuente(FUENTE_LUGARES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                lugares.map { l ->
                    Feature.fromGeometry(Point.fromLngLat(l.coordenada.lon, l.coordenada.lat)).apply {
                        addStringProperty("id", l.id)
                        addStringProperty("nombre", l.nombre)
                    }
                },
            ),
        )
    }

    fun mostrarAmigos(amigos: List<MarcadorAmigo>) {
        fuente(FUENTE_AMIGOS)?.setGeoJson(
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

    // ------------------------------------------------------------------ 3. Rutas

    /** La seleccionada va al final y con mayor orden para dibujarse encima de las demás. */
    fun trazarRutas(rutas: List<TrazoRuta>) {
        fuente(FUENTE_RUTAS)?.setGeoJson(
            FeatureCollection.fromFeatures(
                rutas.filter { it.puntos.size > 1 }.sortedBy { it.seleccionada }.map { r ->
                    Feature.fromGeometry(LineString.fromLngLats(r.puntos.map { Point.fromLngLat(it.lon, it.lat) })).apply {
                        addStringProperty("id", r.id)
                        addStringProperty("color", r.colorHex)
                        addBooleanProperty("sel", r.seleccionada)
                        addNumberProperty("orden", if (r.seleccionada) 1 else 0)
                    }
                },
            ),
        )
    }

    // ------------------------------------------------------------------ 4. Superposiciones

    fun mostrarSuperposiciones(lista: List<Superposicion>) {
        fuente(FUENTE_SUPERPOSICIONES)?.setGeoJson(
            FeatureCollection.fromFeatures(
                lista.map { s ->
                    val anillo = when (s) {
                        is Superposicion.Circulo -> (0..64).map { i ->
                            val p = destino(s.centro, i * 360.0 / 64, s.radioKm)
                            Point.fromLngLat(p.lon, p.lat)
                        }
                        is Superposicion.Rectangulo -> listOf(
                            Point.fromLngLat(s.suroeste.lon, s.suroeste.lat),
                            Point.fromLngLat(s.noreste.lon, s.suroeste.lat),
                            Point.fromLngLat(s.noreste.lon, s.noreste.lat),
                            Point.fromLngLat(s.suroeste.lon, s.noreste.lat),
                            Point.fromLngLat(s.suroeste.lon, s.suroeste.lat),
                        )
                    }
                    Feature.fromGeometry(Polygon.fromLngLats(listOf(anillo)))
                },
            ),
        )
    }

    // ------------------------------------------------------------------ Cámara

    fun ejecutar(comando: ComandoMapa) {
        when (comando) {
            is ComandoMapa.MoverCamara -> mapa.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(comando.destino.lat, comando.destino.lon), comando.zoom),
                900,
            )
            is ComandoMapa.Encuadrar -> {
                val distintos = comando.puntos.distinct()
                if (distintos.size < 2) {
                    distintos.firstOrNull()?.let { mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), 15.0), 700) }
                    return
                }
                val limites = LatLngBounds.Builder().includes(distintos.map { LatLng(it.lat, it.lon) }).build()
                val diagonalKm = distanciaKm(
                    Coordenada(limites.latitudeSouth, limites.longitudeWest),
                    Coordenada(limites.latitudeNorth, limites.longitudeEast),
                )
                if (diagonalKm < 0.2) {
                    // Recorridos de pocos metros: encuadrarlos acercaría el mapa al máximo
                    mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(limites.center, 16.5), 700)
                } else {
                    mapa.animateCamera(CameraUpdateFactory.newLatLngBounds(limites, comando.margenPx), 800)
                }
            }
        }
    }

    /** Recuadro que se ve en pantalla (proyección pantalla → coordenadas). */
    fun limitesVisibles(): Pair<Coordenada, Coordenada>? = runCatching {
        val b = mapa.projection.visibleRegion.latLngBounds
        Coordenada(b.latitudeSouth, b.longitudeWest) to Coordenada(b.latitudeNorth, b.longitudeEast)
    }.getOrNull()

    /** Centro actual de la cámara (para el selector con pin fijo en el centro). */
    fun centro(): Coordenada? = mapa.cameraPosition.target?.let { Coordenada(it.latitude, it.longitude) }

    // ------------------------------------------------------------------ Eventos

    fun escucharEventos(
        onToque: (Coordenada) -> Unit,
        onToqueRuta: () -> ((String) -> Unit)?,
        onToqueLugar: () -> ((String) -> Unit)?,
        onCamaraQuieta: (PosicionCamara) -> Unit,
        onArrastre: () -> Unit,
        onLimitesVisibles: (Coordenada, Coordenada) -> Unit = { _, _ -> },
    ) {
        mapa.addOnCameraIdleListener {
            mapa.cameraPosition.target?.let {
                onCamaraQuieta(PosicionCamara(Coordenada(it.latitude, it.longitude), mapa.cameraPosition.zoom))
            }
            limitesVisibles()?.let { (sw, ne) -> onLimitesVisibles(sw, ne) }
        }
        mapa.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) = onArrastre()
            override fun onMove(detector: MoveGestureDetector) = Unit
            override fun onMoveEnd(detector: MoveGestureDetector) = Unit
        })
        mapa.addOnMapClickListener { punto ->
            // ¿Qué hay bajo el dedo? Se consulta lo dibujado en una zona de ±24 px
            val p = mapa.projection.toScreenLocation(punto)
            val zona = RectF(p.x - 24, p.y - 24, p.x + 24, p.y + 24)
            onToqueLugar()?.let { alTocar ->
                val id = mapa.queryRenderedFeatures(zona, CAPA_LUGARES, CAPA_LUGARES_TEXTO).firstNotNullOfOrNull { it.getStringProperty("id") }
                if (id != null) {
                    alTocar(id)
                    return@addOnMapClickListener true
                }
            }
            onToqueRuta()?.let { alTocar ->
                val id = mapa.queryRenderedFeatures(zona, CAPA_RUTAS).firstNotNullOfOrNull { it.getStringProperty("id") }
                if (id != null) {
                    alTocar(id)
                    return@addOnMapClickListener true
                }
            }
            onToque(Coordenada(punto.latitude, punto.longitude))
            true
        }
    }

    // ------------------------------------------------------------------ Capas

    private fun fuente(id: String) = estilo?.getSourceAs<GeoJsonSource>(id)

    private fun puntos(cs: List<Coordenada>) =
        FeatureCollection.fromFeatures(cs.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) })

    /** Capas propias sobre el mapa base, de abajo hacia arriba. */
    private fun prepararCapas(s: Style) {
        val densidad = context.resources.displayMetrics.density

        // Superposiciones: relleno lavanda translúcido con borde punteado
        s.addSource(GeoJsonSource(FUENTE_SUPERPOSICIONES))
        s.addLayer(FillLayer(CAPA_SUPERPOSICION_RELLENO, FUENTE_SUPERPOSICIONES).withProperties(
            fillColor(Color.parseColor("#7668E8")), fillOpacity(0.08f),
        ))
        s.addLayer(LineLayer(CAPA_SUPERPOSICION_BORDE, FUENTE_SUPERPOSICIONES).withProperties(
            lineColor(Color.parseColor("#7668E8")), lineWidth(2f), lineOpacity(0.7f), lineDasharray(arrayOf(2f, 2f)),
        ))

        // Rutas: borde blanco en la seleccionada; las demás más delgadas y translúcidas
        val seleccionada = Expression.toBool(Expression.get("sel"))
        s.addSource(GeoJsonSource(FUENTE_RUTAS))
        s.addLayer(LineLayer(CAPA_RUTAS_BORDE, FUENTE_RUTAS).withProperties(
            lineColor(Color.WHITE), lineWidth(11f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(seleccionada))
        s.addLayer(LineLayer(CAPA_RUTAS, FUENTE_RUTAS).withProperties(
            lineColor(Expression.toColor(Expression.get("color"))),
            lineWidth(Expression.switchCase(seleccionada, Expression.literal(6f), Expression.literal(4f))),
            lineOpacity(Expression.switchCase(seleccionada, Expression.literal(1f), Expression.literal(0.5f))),
            lineSortKey(Expression.get("orden")),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))

        // Lugares sugeridos: punto blanco con borde morado y nombre
        s.addSource(GeoJsonSource(FUENTE_LUGARES))
        s.addLayer(CircleLayer(CAPA_LUGARES, FUENTE_LUGARES).withProperties(
            circleRadius(8f), circleColor(Color.WHITE), circleStrokeWidth(3f), circleStrokeColor(Color.parseColor("#7668E8")),
        ))
        s.addLayer(SymbolLayer(CAPA_LUGARES_TEXTO, FUENTE_LUGARES).withProperties(
            textField(Expression.get("nombre")), textFont(arrayOf("Noto Sans Regular")), textSize(11f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 1.1f)), textOptional(true),
            textColor(Color.parseColor("#5144B8")), textHaloColor(Color.WHITE), textHaloWidth(1.5f),
        ))

        // Mi posición: punto morado con halo
        s.addSource(GeoJsonSource(FUENTE_YO))
        s.addLayer(CircleLayer(CAPA_YO_HALO, FUENTE_YO).withProperties(
            circleRadius(20f), circleColor(Color.parseColor("#7668E8")), circleOpacity(0.18f),
        ))
        s.addLayer(CircleLayer(CAPA_YO, FUENTE_YO).withProperties(
            circleRadius(8f), circleColor(Color.parseColor("#5144B8")), circleStrokeWidth(3f), circleStrokeColor(Color.WHITE),
        ))

        // Fin de una ruta lineal: punto oscuro con borde blanco
        s.addSource(GeoJsonSource(FUENTE_FIN))
        s.addLayer(CircleLayer(CAPA_FIN, FUENTE_FIN).withProperties(
            circleRadius(9f), circleColor(Color.parseColor("#172033")), circleStrokeWidth(4f), circleStrokeColor(Color.WHITE),
        ))

        // Amigos corriendo: su avatar en círculo y el nombre debajo
        val tamanoAvatar = (44 * densidad).toInt()
        AVATARES.forEach { id -> s.addImage(id, avatarCircular(id, tamanoAvatar)) }
        s.addSource(GeoJsonSource(FUENTE_AMIGOS))
        s.addLayer(SymbolLayer(CAPA_AMIGOS, FUENTE_AMIGOS).withProperties(
            iconImage(Expression.get("avatar")), iconAllowOverlap(true), iconIgnorePlacement(true),
            textField(Expression.get("nombre")), textFont(arrayOf("Noto Sans Regular")), textSize(12f),
            textAnchor(Property.TEXT_ANCHOR_TOP), textOffset(arrayOf(0f, 1.9f)),
            textColor(Color.parseColor("#172033")), textHaloColor(Color.WHITE), textHaloWidth(1.5f), textAllowOverlap(true),
        ))

        // Inicio / punto elegido: lima con borde oscuro
        s.addSource(GeoJsonSource(FUENTE_INICIO))
        s.addLayer(CircleLayer(CAPA_INICIO, FUENTE_INICIO).withProperties(
            circleRadius(10f), circleColor(Color.parseColor("#D5F36A")), circleStrokeWidth(4f), circleStrokeColor(Color.parseColor("#172033")),
        ))

        // Destino: pin con bandera (anotación con ícono propio)
        s.addImage(ICONO_DESTINO, pinDestino((40 * densidad).toInt()))
        s.addSource(GeoJsonSource(FUENTE_DESTINO))
        s.addLayer(SymbolLayer(CAPA_DESTINO, FUENTE_DESTINO).withProperties(
            iconImage(ICONO_DESTINO), iconAnchor(Property.ICON_ANCHOR_BOTTOM), iconAllowOverlap(true), iconIgnorePlacement(true),
        ))
    }

    /** Avatar recortado en círculo con anillo morado y blanco. */
    private fun avatarCircular(id: String, tamano: Int): Bitmap {
        val original = BitmapFactory.decodeResource(context.resources, recursoAvatar(id))
        val salida = Bitmap.createBitmap(tamano, tamano, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(salida)
        val c = tamano / 2f
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
        pincel.color = Color.parseColor("#7668E8")
        lienzo.drawCircle(c, c, c, pincel)
        pincel.color = Color.WHITE
        lienzo.drawCircle(c, c, c * 0.88f, pincel)
        val r = c * 0.80f
        lienzo.save()
        lienzo.clipPath(Path().apply { addCircle(c, c, r, Path.Direction.CW) })
        lienzo.drawBitmap(original, null, RectF(c - r, c - r, c + r, c + r), Paint(Paint.FILTER_BITMAP_FLAG))
        lienzo.restore()
        original.recycle()
        return salida
    }

    /** Pin de destino: gota oscura con borde blanco y una bandera lima. */
    private fun pinDestino(tamano: Int): Bitmap {
        val alto = (tamano * 1.25f).toInt()
        val salida = Bitmap.createBitmap(tamano, alto, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(salida)
        val c = tamano / 2f
        val r = c * 0.92f
        val gota = Path().apply {
            addCircle(c, c, r, Path.Direction.CW)
            moveTo(c - r * 0.55f, c + r * 0.8f)
            lineTo(c, alto.toFloat())
            lineTo(c + r * 0.55f, c + r * 0.8f)
            close()
        }
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
        pincel.color = Color.WHITE
        lienzo.drawPath(gota, pincel)
        lienzo.save()
        lienzo.scale(0.84f, 0.84f, c, alto * 0.45f)
        pincel.color = Color.parseColor("#172033")
        lienzo.drawPath(gota, pincel)
        lienzo.restore()
        // Asta y bandera
        pincel.color = Color.WHITE
        pincel.strokeWidth = tamano * 0.06f
        lienzo.drawLine(c - r * 0.25f, c - r * 0.5f, c - r * 0.25f, c + r * 0.5f, pincel)
        pincel.color = Color.parseColor("#D5F36A")
        lienzo.drawPath(Path().apply {
            moveTo(c - r * 0.22f, c - r * 0.5f)
            lineTo(c + r * 0.45f, c - r * 0.28f)
            lineTo(c - r * 0.22f, c - r * 0.05f)
            close()
        }, pincel)
        return salida
    }

    private companion object {
        const val FUENTE_SUPERPOSICIONES = "superposiciones"
        const val CAPA_SUPERPOSICION_RELLENO = "superposiciones-relleno"
        const val CAPA_SUPERPOSICION_BORDE = "superposiciones-borde"
        const val FUENTE_RUTAS = "rutas"
        const val CAPA_RUTAS_BORDE = "rutas-borde"
        const val CAPA_RUTAS = "rutas-linea"
        const val FUENTE_LUGARES = "lugares"
        const val CAPA_LUGARES = "lugares-punto"
        const val CAPA_LUGARES_TEXTO = "lugares-texto"
        const val FUENTE_YO = "mi-ubicacion"
        const val CAPA_YO_HALO = "mi-ubicacion-halo"
        const val CAPA_YO = "mi-ubicacion-punto"
        const val FUENTE_FIN = "fin-ruta"
        const val CAPA_FIN = "fin-ruta-capa"
        const val FUENTE_AMIGOS = "amigos-en-vivo"
        const val CAPA_AMIGOS = "amigos-en-vivo-capa"
        const val FUENTE_INICIO = "inicio"
        const val CAPA_INICIO = "inicio-capa"
        const val FUENTE_DESTINO = "destino"
        const val CAPA_DESTINO = "destino-capa"
        const val ICONO_DESTINO = "icono-destino"
    }
}
