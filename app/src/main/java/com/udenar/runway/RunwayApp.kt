package com.udenar.runway

import android.app.Application
import com.udenar.runway.di.AppContainer
import com.udenar.runway.data.sync.Sincronizador
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/** Punto de arranque del proceso: inicia el motor de mapas y el contenedor de dependencias. */
class RunwayApp : Application() {

    lateinit var contenedor: AppContainer
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // El motor de MapLibre debe iniciarse antes de crear cualquier MapView.
        // No requiere clave de API: las teselas vienen de OpenFreeMap (OpenStreetMap).
        MapLibre.getInstance(this)
        contenedor = AppContainer(this)
        programarSincronizacion()
    }

    /**
     * Cada vez que hay algo pendiente y una sesión activa, se programa la sincronización.
     * WorkManager espera a que haya red, así que funciona también para lo guardado sin internet.
     */
    private fun programarSincronizacion() {
        alcance.launch {
            combine(contenedor.sincronizador.pendientes, contenedor.cuenta.estadoSesion) { pendientes, sesion ->
                pendientes to (sesion is SessionStatus.Authenticated)
            }.distinctUntilChanged().collect { (pendientes, conSesion) ->
                if (pendientes > 0 && conSesion) Sincronizador.programar(this@RunwayApp)
            }
        }
    }
}
