# RUNWAY

App Android de running para Ipiales (Nariño): planifica recorridos con distancia objetivo, registra el entrenamiento por GPS aunque no haya internet y guarda un catálogo propio de rutas.

Proyecto de clase sobre **integración de mapas en el desarrollo móvil**: el mapa no es solo un elemento gráfico, sino un flujo de información y control que atraviesa las capas de la arquitectura (MVVM).

## Tecnologías (todas gratuitas)

| Componente | Tecnología |
|---|---|
| Lenguaje e interfaz | Kotlin + Jetpack Compose |
| Arquitectura | MVVM (ViewModel + StateFlow) con inyección manual (`di/AppContainer`) |
| Mapa | MapLibre Native Android (`MapView`) |
| Teselas | OpenFreeMap (datos de OpenStreetMap, sin clave de API) |
| Ubicación | `android.location.LocationManager` (sin Google Play Services) |
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

| # | Parte | Estado |
|---|---|---|
| 0 | Base del proyecto, dependencias, permisos y repositorio | Hecha |
| 1 | Mapa de Ipiales: `MapView`, teselas, gestos, atribución | |
| 2 | Ubicación: permisos, posición en vivo, centrar en mí | |
| 3 | Valhalla: ruta A→B tocando el mapa y polilínea | |
| 4 | Planificador: distancia, modalidad y nivel; circuitos y alternativas | |
| 5 | Room: catálogo de rutas privadas y reutilizables | |
| 6 | Entrenamiento: servicio en primer plano con GPS guardado en Room | |
| 7 | Historial y estadísticas | |
| 8 | Supabase: cuentas y sincronización diferida | |
| 9 | Cierre y pruebas de campo | |

## Compilar

Abrir la carpeta en Android Studio. Las claves de Supabase van en `local.properties` (no se sube a GitHub):

```
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```
