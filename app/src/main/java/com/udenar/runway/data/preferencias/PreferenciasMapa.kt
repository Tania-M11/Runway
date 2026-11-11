package com.udenar.runway.data.preferencias

import android.content.Context
import androidx.core.content.edit
import com.udenar.runway.domain.CAMARA_POR_DEFECTO
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.PosicionCamara

/** Recuerda la última vista del mapa para reabrirlo donde el usuario lo dejó. */
class PreferenciasMapa(context: Context) {

    private val prefs = context.getSharedPreferences("mapa", Context.MODE_PRIVATE)

    fun ultimaCamara(): PosicionCamara {
        if (!prefs.contains(LAT)) return CAMARA_POR_DEFECTO
        return PosicionCamara(
            Coordenada(
                Double.fromBits(prefs.getLong(LAT, 0)),
                Double.fromBits(prefs.getLong(LON, 0)),
            ),
            Double.fromBits(prefs.getLong(ZOOM, 0)),
        )
    }

    fun guardarCamara(camara: PosicionCamara) = prefs.edit {
        putLong(LAT, camara.centro.lat.toRawBits())
        putLong(LON, camara.centro.lon.toRawBits())
        putLong(ZOOM, camara.zoom.toRawBits())
    }

    private companion object {
        const val LAT = "lat"
        const val LON = "lon"
        const val ZOOM = "zoom"
    }
}
