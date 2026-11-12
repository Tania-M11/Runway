package com.udenar.runway.data.offline

import android.content.Context
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.ProgresoDescarga
import com.udenar.runway.domain.Teselas
import com.udenar.runway.domain.ZonaOffline
import com.udenar.runway.ui.mapa.ESTILO_MAPA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.coroutines.resume

/**
 * Mapas sin conexión con el gestor de descargas del motor de MapLibre (mecanismo 3:
 * operaciones automáticas del motor).
 *
 * La app solo define **qué área** y **qué zooms** quiere; el motor resuelve el resto: descarga
 * el estilo, las fuentes, los íconos y cada tesela del área, los guarda en su base de datos
 * interna y, cuando no hay internet, el `MapView` los usa solo, sin cambiar nada en el código.
 */
class RepositorioMapasOffline(private val context: Context) {

    private val gestor by lazy { OfflineManager.getInstance(context) }
    // Las llamadas del gestor responden en el hilo principal
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Regiones descargándose: se guardan para que el recolector de basura no corte su observador. */
    private val activas = mutableMapOf<String, OfflineRegion>()

    private val _zonas = MutableStateFlow<List<ZonaOffline>>(emptyList())
    val zonas: StateFlow<List<ZonaOffline>> = _zonas.asStateFlow()

    /** Descargas en curso, por clave (la pantalla que las inició sabe cuál es la suya). */
    private val _descargas = MutableStateFlow<Map<String, ProgresoDescarga>>(emptyMap())
    val descargas: StateFlow<Map<String, ProgresoDescarga>> = _descargas.asStateFlow()

    init {
        alcance.launch { refrescar() }
    }

    sealed interface Resultado {
        data object Iniciada : Resultado
        data class MuyGrande(val teselas: Long) : Resultado
    }

    /** Clave estable para un recuadro (identifica la descarga mientras avanza). */
    fun clave(sw: Coordenada, ne: Coordenada) = "%.4f,%.4f,%.4f,%.4f".format(java.util.Locale.US, sw.lat, sw.lon, ne.lat, ne.lon)

    fun descargar(nombre: String, sw: Coordenada, ne: Coordenada): Resultado {
        val teselas = Teselas.contar(sw, ne)
        if (teselas > Teselas.LIMITE) return Resultado.MuyGrande(teselas)
        val clave = clave(sw, ne)
        if (_descargas.value[clave]?.let { !it.terminada && it.error == null } == true) return Resultado.Iniciada

        val definicion = OfflineTilePyramidRegionDefinition(
            ESTILO_MAPA,
            LatLngBounds.Builder().include(LatLng(sw.lat, sw.lon)).include(LatLng(ne.lat, ne.lon)).build(),
            Teselas.ZOOM_MIN,
            Teselas.ZOOM_MAX,
            context.resources.displayMetrics.density,
        )
        val metadatos = JSONObject()
            .put("nombre", nombre)
            .put("creadaEn", System.currentTimeMillis())
            .toString().toByteArray()

        _descargas.update { it + (clave to ProgresoDescarga(0, teselas, 0)) }
        gestor.createOfflineRegion(definicion, metadatos, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) = observarYActivar(offlineRegion, clave)
            override fun onError(error: String) {
                _descargas.update { it + (clave to ProgresoDescarga(0, 0, 0, error = error)) }
            }
        })
        return Resultado.Iniciada
    }

    /** Continúa una zona que quedó a medias (p. ej. se cerró la app o se perdió la red). */
    fun reanudar(id: Long) {
        gestor.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val region = offlineRegions?.firstOrNull { it.id == id } ?: return
                val limites = (region.definition as? OfflineTilePyramidRegionDefinition)?.bounds ?: return
                observarYActivar(
                    region,
                    clave(Coordenada(limites.latitudeSouth, limites.longitudeWest), Coordenada(limites.latitudeNorth, limites.longitudeEast)),
                )
            }
            override fun onError(error: String) = Unit
        })
    }

    private fun observarYActivar(region: OfflineRegion, clave: String) {
        activas[clave] = region
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                val progreso = ProgresoDescarga(
                    completados = status.completedResourceCount,
                    requeridos = status.requiredResourceCount,
                    bytes = status.completedResourceSize,
                    terminada = status.isComplete,
                )
                _descargas.update { it + (clave to progreso) }
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    activas.remove(clave)
                    alcance.launch { refrescar() }
                }
            }

            override fun onError(error: OfflineRegionError) {
                // Errores temporales (p. ej. sin red): el motor reintenta solo; se informa sin cancelar
                _descargas.update { m ->
                    val actual = m[clave] ?: ProgresoDescarga(0, 0, 0)
                    m + (clave to actual.copy(error = error.message))
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                _descargas.update { it + (clave to ProgresoDescarga(0, 0, 0, error = "La zona supera el límite de teselas")) }
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        alcance.launch { refrescar() }
    }

    suspend fun eliminar(id: Long) {
        val region = listar().firstOrNull { it.id == id } ?: return
        suspendCancellableCoroutine { cont ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = cont.resume(Unit)
                override fun onError(error: String) = cont.resume(Unit)
            })
        }
        refrescar()
    }

    suspend fun refrescar() {
        _zonas.value = listar().mapNotNull { region ->
            val limites = (region.definition as? OfflineTilePyramidRegionDefinition)?.bounds ?: return@mapNotNull null
            val meta = runCatching { JSONObject(String(region.metadata)) }.getOrNull()
            val estado = estadoDe(region)
            ZonaOffline(
                id = region.id,
                nombre = meta?.optString("nombre").orEmpty().ifBlank { "Zona descargada" },
                suroeste = Coordenada(limites.latitudeSouth, limites.longitudeWest),
                noreste = Coordenada(limites.latitudeNorth, limites.longitudeEast),
                creadaEn = meta?.optLong("creadaEn") ?: 0L,
                completa = estado?.isComplete == true,
                bytes = estado?.completedResourceSize ?: 0L,
                recursosCompletados = estado?.completedResourceCount ?: 0L,
                recursosRequeridos = estado?.requiredResourceCount ?: 0L,
            )
        }.sortedByDescending { it.creadaEn }
    }

    private suspend fun listar(): List<OfflineRegion> = suspendCancellableCoroutine { cont ->
        gestor.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) = cont.resume(offlineRegions?.toList().orEmpty())
            override fun onError(error: String) = cont.resume(emptyList())
        })
    }

    private suspend fun estadoDe(region: OfflineRegion): OfflineRegionStatus? = suspendCancellableCoroutine { cont ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) = cont.resume(status)
            override fun onError(error: String?) = cont.resume(null)
        })
    }
}
