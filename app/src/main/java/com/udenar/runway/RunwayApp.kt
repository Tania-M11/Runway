package com.udenar.runway

import android.app.Application
import com.udenar.runway.di.AppContainer

/** Punto de arranque del proceso: crea una sola vez el contenedor de dependencias. */
class RunwayApp : Application() {

    lateinit var contenedor: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        contenedor = AppContainer(this)
    }
}
