package com.udenar.runway.di

import android.content.Context

/**
 * Inyección de dependencias manual.
 *
 * Crea una sola instancia de cada fuente de datos (capa de datos) y la entrega a los
 * ViewModel (capa de presentación). Así ninguna pantalla habla directo con el GPS,
 * con Valhalla ni con la base de datos: todo pasa por un repositorio.
 *
 * Se va llenando por partes:
 *  - Parte 2: repositorio de ubicación (android.location.LocationManager)
 *  - Parte 3: cliente de rutas (Valhalla)
 *  - Parte 5: base de datos local (Room / SQLite)
 *  - Parte 8: Supabase (autenticación y sincronización)
 */
class AppContainer(val context: Context)
