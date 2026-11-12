package com.udenar.runway.di

import android.content.Context
import androidx.room.Room
import com.udenar.runway.BuildConfig
import com.udenar.runway.data.amigos.AmigosEnVivo
import com.udenar.runway.data.offline.RepositorioMapasOffline
import com.udenar.runway.data.amigos.PublicadorUbicacion
import com.udenar.runway.data.amigos.RepositorioAmigos
import com.udenar.runway.data.cuenta.RepositorioCuenta
import com.udenar.runway.data.red.JsonRunway
import com.udenar.runway.data.sync.Sincronizador
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.udenar.runway.data.entrenamiento.SesionEntrenamiento
import com.udenar.runway.data.entrenamiento.Vibrador
import com.udenar.runway.data.ubicacion.Brujula
import com.udenar.runway.data.ubicacion.Satelites
import com.udenar.runway.data.local.RepositorioEntrenamientos
import com.udenar.runway.data.local.RepositorioRutas
import com.udenar.runway.data.local.RunwayDatabase
import com.udenar.runway.data.lugares.BuscadorLugares
import com.udenar.runway.data.lugares.GeocodificadorAndroid
import com.udenar.runway.data.preferencias.PreferenciasMapa
import com.udenar.runway.data.preferencias.PreferenciasPerfil
import com.udenar.runway.data.preferencias.PreferenciasPlan
import com.udenar.runway.data.red.MonitorConexion
import com.udenar.runway.data.red.crearClienteHttp
import com.udenar.runway.data.rutas.ClienteValhalla
import com.udenar.runway.data.rutas.PlanificadorRutas
import com.udenar.runway.data.ubicacion.RepositorioUbicacion

/**
 * Inyección de dependencias manual.
 *
 * Crea una sola instancia de cada fuente de datos (capa de datos) y la entrega a los
 * ViewModel (capa de presentación). Así ninguna pantalla habla directo con el GPS,
 * con los servicios de mapas ni con la base de datos: todo pasa por esta capa.
 *
 * Supabase: cuentas (parte 8); sincronización y amigos en las partes 9 y 10.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val http by lazy { crearClienteHttp() }
    val monitorConexion by lazy { MonitorConexion(app) }
    val preferenciasMapa by lazy { PreferenciasMapa(app) }
    val preferenciasPerfil by lazy { PreferenciasPerfil(app) }
    val buscadorLugares by lazy { BuscadorLugares(http, GeocodificadorAndroid(app)) }
    val ubicacion by lazy { RepositorioUbicacion(app) }
    val preferenciasPlan by lazy { PreferenciasPlan(app) }
    val planificador by lazy { PlanificadorRutas(ClienteValhalla(http)) }

    // Base de datos SQLite local (Room)
    private val baseDatos by lazy {
        Room.databaseBuilder(app, RunwayDatabase::class.java, "runway.db").build()
    }
    val rutas by lazy { RepositorioRutas(baseDatos.rutas()) }
    val entrenamientos by lazy { RepositorioEntrenamientos(baseDatos.entrenamientos()) }

    /** Sesión de entrenamiento única, compartida por la pantalla y el servicio en primer plano. */
    val sesion by lazy { SesionEntrenamiento(app, ubicacion, baseDatos.entrenamientos(), publicador, Vibrador(app)) }
    val brujula by lazy { Brujula(app) }
    val satelites by lazy { Satelites(app) }

    // Supabase: la URL y la clave publicable vienen de local.properties (BuildConfig)
    val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://sin-configurar.supabase.co" },
            supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifBlank { "sin-configurar" },
        ) {
            defaultSerializer = KotlinXSerializer(JsonRunway)
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }
    val mapasOffline by lazy { RepositorioMapasOffline(app) }
    val amigos by lazy { RepositorioAmigos(supabase) }
    val amigosEnVivo by lazy { AmigosEnVivo(supabase) }
    private val publicador by lazy { PublicadorUbicacion(supabase, preferenciasPerfil) }
    val sincronizador by lazy { Sincronizador(supabase, baseDatos.rutas(), baseDatos.entrenamientos()) }
    val cuenta by lazy {
        RepositorioCuenta(
            supabase,
            preferenciasPerfil,
            sincronizador,
            borrarDatosLocales = { withContext(Dispatchers.IO) { baseDatos.clearAllTables() } },
        )
    }
}
