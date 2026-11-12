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
| Gráficas | Canvas de Compose (barras propias, sin librería) |

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

## MapController: `ui/mapa/ControladorMapa.kt`

La guía de recursos describe al controlador del mapa (GoogleMap / MapController) como lo que permite *personalizar el estado inicial del mapa, añadir marcadores, trazar rutas y gestionar superposiciones (overlays) o anotaciones*. En RUNWAY esas cuatro operaciones están en una sola clase que envuelve al controlador del motor (`MapLibreMap`):

| Operación | Método | Dónde se ve en la app |
|---|---|---|
| Estado inicial | `configurarEstadoInicial` | Cámara, zoom mínimo y máximo y brújula al abrir cualquier mapa |
| Marcadores | `mostrarMarcador`, `mostrarLugares`, `mostrarAmigos` | Inicio (lima), destino (bandera), mi posición, lugares sugeridos y avatares de amigos |
| Rutas | `trazarRutas` | Alternativas en colores, trazado GPS en vivo, ruta de referencia |
| Superposiciones | `mostrarSuperposiciones` | Círculo con el alcance de la distancia al elegir destino; área descargada (parte 12) |

Además ejecuta las órdenes de cámara (mover, encuadrar) y registra los eventos: toque, toque sobre una ruta o un marcador (`queryRenderedFeatures`), cámara detenida y arrastre.

## Mapas sin conexión (mecanismo 3)

`data/offline/RepositorioMapasOffline.kt` usa el `OfflineManager` de MapLibre. La app solo define el área (un recuadro) y los zooms (11 a 16). El motor descarga por su cuenta el estilo, las fuentes, los íconos y todas las teselas del área, informa el progreso y, cuando no hay internet, el `MapView` las usa sin ningún cambio en el código. Antes de descargar, `domain/MapasOffline.kt` estima cuántas teselas son (en el zoom z hay 2^z × 2^z teselas en el mundo) para no bajar zonas enormes del servidor público.

## Guía en vivo durante el entrenamiento

- **Hardware (mecanismo 1):** el rumbo del GPS (`Location.bearing`) orienta el mapa al correr; quieto, se usa la brújula (sensor de rotación, `data/ubicacion/Brujula.kt`). Los avisos se dan con el vibrador.
- **Cámara (mecanismo 4):** `ComandoMapa.Seguir` mueve la cámara con rumbo e inclinación 3D; un botón alterna entre modo guía y norte arriba.
- **Cálculo (`domain/Guia.kt`):** la posición se proyecta sobre el segmento más cercano de la ruta para saber los km recorridos y restantes y la distancia a la ruta. En un circuito se busca cerca del último segmento conocido, para no confundir el final con el inicio. El aviso de desvío tiene histéresis: sale a más de 30 m y vuelve a menos de 20 m.
- **Funciona con la pantalla apagada:** la guía corre dentro de la sesión de entrenamiento (servicio en primer plano). La notificación muestra "faltan X km" y el teléfono vibra al desviarse, al volver y al llegar.

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
| 7 | Historial (7 días, 30 días, 12 meses), gráficas, récords e Inicio con datos reales | 5.10, 5.2 | Hecha |
| 8 | Cuentas: bienvenida, registro con avatar, inicio de sesión, perfil con código de amigo | Acceso, 5.12 | Hecha |
| 9 | Sincronización de rutas y entrenamientos con Supabase (WorkManager) | 7 | Hecha |
| 10 | Amigos por código con aceptación y ubicación en vivo mientras entrenan (Realtime) | — | Hecha |
| 11 | Destino y sugerencias: elegir a dónde llegar (mapa con pin central, búsqueda o lugares sugeridos a la distancia pedida), rutas alternativas hacia el destino; ControladorMapa | 5.4–5.6 | Hecha |
| 12 | Mapas sin conexión: descargar la zona de una ruta o lo que se ve en Explorar, progreso, superposición del área y gestión en Perfil | 5.3 | Hecha |
| 13 | Guía en vivo: mapa orientado (rumbo GPS o brújula) e inclinado, tramo recorrido/pendiente, km restantes, aviso de desvío y llegada con vibración | 5.7 | Hecha |
| 14 | Perfil editable y pruebas de campo | 5.12 | |

## Backend (Supabase)

Proyecto `runway`. Los esquemas están en `supabase/` y se aplican en orden:

1. `01_perfiles.sql`: perfil por cuenta (nombre, avatar, código de amigo, nivel y desnivel), creado por un trigger al registrarse.
2. `02_sincronizacion.sql`: copia remota de rutas y entrenamientos con seguridad por filas (cada cuenta solo ve lo suyo).

3. `03_amigos.sql`: amistades con aceptación (funciones `solicitar_amistad`, `responder_solicitud`, `eliminar_amistad`, `mis_amigos`) y `ubicaciones_en_vivo`, publicada en Supabase Realtime. Solo los amigos aceptados ven la ubicación, y solo existe mientras el entrenamiento está activo.

En Authentication está apagado "Confirm email" para poder probar con varias cuentas.

## Compilar

Abrir la carpeta en Android Studio. Las claves de Supabase van en `local.properties` (no se sube a GitHub):

```
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```
