package com.udenar.runway

import android.app.Application
import com.udenar.runway.di.AppContainer
import org.maplibre.android.MapLibre

/** Punto de arranque del proceso: inicia el motor de mapas y el contenedor de dependencias. */
class RunwayApp : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // El motor de MapLibre debe iniciarse antes de crear cualquier MapView.
        // No requiere clave de API: las teselas vienen de OpenFreeMap (OpenStreetMap).
        MapLibre.getInstance(this)
        contenedor = AppContainer(this)
    }
}
