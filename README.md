# RUNWAY

App Android de running: planifica recorridos en cualquier lugar (las primeras pruebas de campo son en Ipiales, Nariño) con distancia objetivo, registra el entrenamiento por GPS aunque no haya internet y guarda un catálogo propio de rutas.

Proyecto de clase sobre **integración de mapas en el desarrollo móvil**: el mapa no es solo un elemento gráfico, sino un flujo de información y control que atraviesa las capas de la arquitectura (MVVM).

## Tecnologías (todas gratuitas)

| Componente | Tecnología |
|---|---|
| Lenguaje e interfaz | Kotlin + Jetpack Compose |
| Arquitectura | MVVM (ViewModel + StateFlow) con inyección manual (`di/AppContainer`) |
| Mapa | MapLibre Native Android (`MapView`) |
| Teselas | OpenFreeMap (datos de OpenStreetMap, sin clave de API) |
| Ubicación | `android.location.LocationManager` (sin Google Play Services) |
| Buscador de lugares | Photon (geocodificador gratuito sobre OpenStreetMap) |
| Rutas peatonales | Valhalla, servidor público `valhalla1.openstreetmap.de` |
| Base local (offline) | Room (SQLite) |
| Sincronización diferida | WorkManager (se ejecuta cuando vuelve la red) |
| Backend | Supabase (Auth + PostgreSQL) con `supabase-kt` |
| Gráficas | Vico |

## Mecanismos técnicos de la integración de mapas

| Mecanismo | Dónde está en RUNWAY |
|---|---|
| 1. Acceso al hardware de geolocalización (`android.location`) | `data/location`: `LocationManager` + permisos → `Flow<Location>` |
| 2. Contenedor de interfaz (`MapView` / `MapKit`) | `ui/mapa`: `MapView` de MapLibre dentro de Compose con `AndroidView`, con su ciclo de vida reenviado |
| 3. Operaciones automáticas del motor (conexión al servidor, renderizado y gestos) | Lo hace MapLibre a partir del estilo de OpenFreeMap: descarga y caché de teselas, renderizado en GPU, pellizco, giro e inclinación |
| 4. Control lógico y eventos (`OnMapReadyCallback`, `GoogleMap` / `MapController`) | `getMapAsync(OnMapReadyCallback)` entrega un `MapLibreMap` (equivalente de `GoogleMap`); cámara, capas y escuchas de toque los controla el ViewModel |
| 5. Integración multiplataforma (ecosistema de paquetes) | Gradle + Maven Central (`gradle/libs.versions.toml`); MapLibre tiene el mismo motor para Android, iOS y web |

## Cómo se generan las rutas

Valhalla calcula caminos entre puntos, pero no "un circuito de 5 km". `data/rutas/PlanificadorRutas.kt` lo resuelve así:

1. Prueba 4 direcciones desde el inicio (norte, este, sur, oeste).
2. **Circular:** coloca 2 puntos intermedios formando un triángulo equilátero con el inicio (perímetro = 3r, con r = distancia / (3 × 1,3) porque las calles no son rectas). **Lineal:** un punto final en esa dirección.
3. Pide a Valhalla la ruta peatonal real (con altimetría cada 30 m) y, si se aleja más de 10 % del objetivo, escala el triángulo y repite (hasta 2 correcciones).
4. Puntúa cada alternativa (`domain/Rutas.kt`, `Recomendador`): 50 % coincidencia de distancia, 30 % desnivel según la preferencia (m de subida por km) y 20 % continuidad (giros por km).

## Registro GPS offline-first

- `data/entrenamiento/SesionEntrenamiento.kt` escucha el GPS (`LocationManager`, 1 lectura/s), filtra el ruido (`FiltroGps`: precisión ≤ 25 m, ≥ 3 m entre puntos, ≤ 43 km/h) y guarda **cada punto en SQLite en el momento**, en una transacción con las métricas.
- `ServicioEntrenamiento` es un servicio en primer plano de tipo `location`: mantiene el registro con la pantalla apagada y muestra distancia y cronómetro en la notificación.
- Si Android cierra la app a mitad de un entrenamiento, al abrirla de nuevo se recupera desde SQLite como pausado (y si el sistema reinicia el servicio, sigue registrando).
- La base de datos pasó a la versión 2 con una migración automática de Room: las rutas guardadas se conservan.

## Estructura

```
com.udenar.runway
├─ data/          capa de datos
│  ├─ location/   GPS (android.location)
│  ├─ routing/    cliente de Valhalla
│  ├─ local/      Room (SQLite)
│  └─ remote/     Supabase
├─ domain/        modelos y reglas puras (distancia, ritmo, puntuación de rutas)
├─ ui/            pantallas Compose + ViewModel por módulo
└─ di/            AppContainer
```

## Plan de desarrollo por partes

Sigue la especificación funcional y visual (`Especificacion_funcional_y_visual_RUNWAY.docx`): morado `#7668E8`, lima `#D5F36A`, tarjetas redondeadas y barra inferior con Inicio, Explorar, Entrenar, Mis rutas y Perfil.

| # | Parte | Pantallas de la especificación | Estado |
|---|---|---|---|
| 0 | Base del proyecto, dependencias, permisos y repositorio | | Hecha |
| 1 | Sistema visual (colores, Plus Jakarta Sans, componentes) y navegación inferior | 4, Inicio vacío (5.2) | Hecha |
| 2 | Explorar: `MapView`, teselas, gestos, buscador de lugares de cualquier ciudad, error y reintento | 5.3 | Hecha |
| 3 | Ubicación (`LocationManager`), configuración inicial del corredor, posición en vivo y centrar en mí | 5.1, 5.3 | Hecha |
| 4 | Planificar con Valhalla: circuitos, comparación y detalle | 5.4, 5.5, 5.6 | Hecha |
| 5 | Room: guardar rutas (privada o reutilizable) y Mis rutas con filtros, renombrar y eliminar | 5.6, 5.11 | Hecha |
| 6 | Entrenamiento activo con servicio en primer plano, pausa, recuperación tras cierre y resumen | 5.7, 5.8, 5.9 | Hecha |
| 7 | Historial, estadísticas e Inicio con datos reales | 5.10, 5.2 | |
| 8 | Supabase: sesión anónima y sincronización diferida | 7 | |
| 9 | Perfil y pruebas de campo | 5.12 | |

## Compilar

Abrir la carpeta en Android Studio. Las claves de Supabase van en `local.properties` (no se sube a GitHub):

```
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```
