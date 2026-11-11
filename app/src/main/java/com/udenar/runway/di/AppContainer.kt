package com.udenar.runway.di

import android.content.Context
import com.udenar.runway.data.lugares.BuscadorLugares
import com.udenar.runway.data.preferencias.PreferenciasMapa
import com.udenar.runway.data.red.MonitorConexion
import com.udenar.runway.data.red.crearClienteHttp

/**
 * Inyección de dependencias manual.
 *
 * Crea una sola instancia de cada fuente de datos (capa de datos) y la entrega a los
 * ViewModel (capa de presentación). Así ninguna pantalla habla directo con el GPS,
 * con los servicios de mapas ni con la base de datos: todo pasa por esta capa.
 *
 * Pendiente por partes: ubicación (3), Valhalla (4), Room (5) y Supabase (8).
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val http by lazy { crearClienteHttp() }
    val monitorConexion by lazy { MonitorConexion(app) }
    val preferenciasMapa by lazy { PreferenciasMapa(app) }
    val buscadorLugares by lazy { BuscadorLugares(http) }
}
