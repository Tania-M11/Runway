# RUNWAY

App Android de running: planifica recorridos en cualquier lugar con distancia objetivo, registra el entrenamiento por GPS aunque no haya internet y guarda un catálogo propio de rutas.

Proyecto de clase sobre **integración de mapas en el desarrollo móvil**: el mapa no es solo un elemento gráfico, sino un flujo de información y control que atraviesa las capas de la arquitectura (MVVM).

## Tecnologías 

| Componente | Tecnología |
|---|---|
| Lenguaje e interfaz | Kotlin + Jetpack Compose |
| Arquitectura | MVVM (ViewModel + StateFlow) con inyección manual (`di/AppContainer`) |
| Mapa | MapLibre Native Android (`MapView`) |
| Teselas | OpenFreeMap (datos de OpenStreetMap, sin clave de API) |
| Ubicación y sensores | `android.location`: `LocationManager`, `GnssStatus` y `Geocoder` (sin Google Play Services); brújula y vibrador |
| Buscador de lugares | Photon (geocodificador gratuito sobre OpenStreetMap), con `Geocoder` de Android como respaldo |
| Rutas peatonales | Valhalla, servidor público `valhalla1.openstreetmap.de` |
| Base local (offline) | Room (SQLite) |
| Sincronización diferida | WorkManager (se ejecuta cuando vuelve la red) |
| Backend | Supabase (Auth + PostgreSQL) con `supabase-kt` |
| Gráficas | Canvas de Compose (barras propias, sin librería) |

## Teoría de clase → código de RUNWAY

Cada concepto de la guía de recursos, dónde está implementado y cómo verlo en la app.

### 1. Acceso al hardware de geolocalización

**android.location**: paquete del framework de Android que da acceso a los servicios de geolocalización del dispositivo.

| Clase de `android.location` | Uso en RUNWAY | Archivo | Dónde se ve |
|---|---|---|---|
| `LocationManager` + `LocationListener` | Posición en vivo con los proveedores GPS y red (sin Google Play Services) | `data/ubicacion/RepositorioUbicacion.kt` | Punto morado en Explorar; registro del entrenamiento |
| `Location` (precisión, altitud, velocidad, rumbo) | Filtro de ruido, subida acumulada, ritmo, orientación del mapa | `RepositorioUbicacion.kt`, `domain/Entrenamiento.kt` | Métricas del entrenamiento; modo guía |
| `GnssStatus` | Satélites en uso y visibles por constelación (GPS, Galileo, GLONASS, BeiDou) | `data/ubicacion/Satelites.kt` | Pestaña Entrenar, tarjeta "GPS listo" |
| `Geocoder` | Nombre ↔ coordenadas como respaldo cuando el servicio Photon no responde | `data/lugares/GeocodificadorAndroid.kt` | Búsqueda de lugares en Planificar y Explorar |
| `LocationManager.PROVIDERS_CHANGED_ACTION` | Saber si el usuario encendió o apagó la ubicación | `RepositorioUbicacion.kt` | Aviso "La ubicación del teléfono está apagada" |

Además se usan otros sensores del teléfono: la brújula (sensor de rotación, `data/ubicacion/Brujula.kt`) para orientar el mapa estando quieto, y el vibrador para los avisos de desvío y llegada (`data/entrenamiento/Vibrador.kt`). La posición se sigue registrando con la pantalla apagada gracias a un servicio en primer plano de tipo `location` (`data/entrenamiento/ServicioEntrenamiento.kt`).

### 2. Contenedores de interfaz y vistas

**Android: MapView**: el SDK de mapas entrega un contenedor (`MapView` o `SupportMapFragment`) que se inserta en la interfaz.

- RUNWAY usa el `MapView` de MapLibre (`ui/mapa/MapaRunway.kt`), insertado en Jetpack Compose con `AndroidView`.
- Como toda vista de mapa, necesita que se le reenvíe el ciclo de vida de la pantalla (`onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`); lo hace un `LifecycleEventObserver`.
- `SupportMapFragment` es la alternativa basada en Fragments. No se usa porque la app está hecha con Compose, sin Fragments.

**iOS: MapKit**: RUNWAY es Android nativo, así que MapKit no se usa. Equivalencias de lo implementado:

| Android (RUNWAY) | iOS nativo (MapKit / Core Location) |
|---|---|
| `MapView` (MapLibre) | `MKMapView` |
| `OnMapReadyCallback` | `mapViewDidFinishLoadingMap(_:)` del `MKMapViewDelegate` |
| Marcadores (`SymbolLayer` / `CircleLayer`) | `MKAnnotation` + `MKAnnotationView` |
| Trazar rutas (`LineLayer`) | `MKPolyline` + `MKPolylineRenderer` |
| Superposiciones (`FillLayer`) | `MKOverlay` (`MKCircle`, `MKPolygon`) |
| Cámara con rumbo e inclinación | `MKMapCamera` (`heading`, `pitch`) |
| `LocationManager` / `GnssStatus` | `CLLocationManager` / `CLLocation` |
| `Geocoder` | `CLGeocoder` |
| `OfflineManager` (mapas sin conexión) | No hay equivalente directo en MapKit |

MapLibre también tiene una versión nativa para iOS que lee el mismo estilo de OpenFreeMap, así que el mapa se vería igual en ambas plataformas.

### 3. Operaciones automáticas del motor de mapas

**Conexión al servidor**: el motor se conecta automáticamente al servidor de teselas y descarga los datos según el área y el zoom visibles, guardándolos en caché.

- Al recibir la URL del estilo (`ESTILO_MAPA`, OpenFreeMap), MapLibre descarga por su cuenta el estilo, las teselas vectoriales, los íconos y las fuentes. La app no programa ninguna petición de teselas.
- Esto se hace visible en los **mapas sin conexión** (`data/offline/RepositorioMapasOffline.kt`): la app solo indica el área y los zooms, y el motor descarga y guarda todo, informa el progreso y luego lo usa sin internet.

**Renderizado y gestos**: visualización dinámica en pantalla y respuesta automática a los gestos táctiles (desplazamiento, rotación y zoom).

- El dibujo en la GPU y los gestos (arrastrar, pellizcar, girar, inclinar) los hace el motor.
- La app se engancha a los gestos: al arrastrar, el mapa deja de seguir al corredor (`onArrastre`).
- La app también controla el renderizado: los **edificios en 3D** del estilo (capa `fill-extrusion`) se encienden en modo guía, se apagan en "norte arriba" y se tiñen con la paleta de RUNWAY (`ControladorMapa.mostrarEdificios3D`).

### 4. Control lógico y eventos

**OnMapReadyCallback**: interfaz que avisa cuando el mapa terminó de cargarse y está listo para recibir instrucciones.

En `MapaRunway.kt`, `mapView.getMapAsync(OnMapReadyCallback { ... })` recibe el controlador del motor. Con él se crea el `ControladorMapa` y se fija el estado inicial. Hay un segundo aviso cuando el estilo terminó de cargar, y solo entonces se dibujan marcadores y rutas.

**GoogleMap / MapController**: permiten personalizar el estado inicial del mapa, añadir marcadores, trazar rutas y gestionar superposiciones o anotaciones.

Todo está en `ui/mapa/ControladorMapa.kt`, que envuelve al `MapLibreMap` (el equivalente de `GoogleMap`):

| Operación de la definición | Método | Ejemplos en la app |
|---|---|---|
| Personalizar el estado inicial | `configurarEstadoInicial` | Cámara inicial, zoom mínimo y máximo, brújula |
| Añadir marcadores | `mostrarMarcador`, `mostrarLugares`, `mostrarAmigos` | Inicio, destino con bandera, mi posición, lugares sugeridos, avatares de amigos en vivo |
| Trazar rutas | `trazarRutas` | Alternativas en colores, recorrido GPS, tramo recorrido y pendiente, línea de regreso |
| Gestionar superposiciones | `mostrarSuperposiciones`, `mostrarEdificios3D` | Círculo de alcance al elegir destino, zonas descargadas |
| Cámara | `ejecutar(ComandoMapa)` | Mover, encuadrar una ruta, seguir con rumbo e inclinación |
| Eventos | `escucharEventos` | Toque en el mapa, toque en una ruta o un lugar (`queryRenderedFeatures`), cámara detenida, arrastre |

El flujo sigue la arquitectura MVVM: el **ViewModel** decide, el `ControladorMapa` ejecuta sobre el mapa y los **eventos** del mapa vuelven al ViewModel.

### 5. Integración en entornos multiplataforma

**Ecosistemas de paquetes**: Pub.dev es el repositorio oficial de paquetes para Dart y Flutter, y npm es el gestor de paquetes por defecto de Node.js (y de React Native).

- RUNWAY es Android nativo en Kotlin. Su ecosistema es **Gradle + Maven Central / Google Maven**, con las versiones centralizadas en `gradle/libs.versions.toml`.
- Equivalentes en otros ecosistemas para las mismas piezas:

| Pieza | Kotlin (Maven) — RUNWAY | Flutter (pub.dev) | React Native (npm) |
|---|---|---|---|
| Mapa | `org.maplibre.gl:android-sdk` | `maplibre_gl` o `flutter_map` | `@maplibre/maplibre-react-native` o `react-native-maps` |
| Ubicación | `android.location` (sistema) | `geolocator` | `react-native-geolocation-service` |
| Base de datos local | `androidx.room` | `drift` o `sqflite` | `react-native-sqlite-storage` o WatermelonDB |
| Backend | `supabase-kt` | `supabase_flutter` | `@supabase/supabase-js` |
| HTTP | `ktor-client` | `http` o `dio` | `fetch` o `axios` |

El plan original de RUNWAY estaba pensado en Flutter con pub.dev (`flutter_map`, `geolocator`, `drift`). La app se construyó en Kotlin con las piezas equivalentes de Maven.

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

## Mapas sin conexión (mecanismo 3)

`data/offline/RepositorioMapasOffline.kt` usa el `OfflineManager` de MapLibre. La app solo define el área (un recuadro) y los zooms (11 a 16). El motor descarga por su cuenta el estilo, las fuentes, los íconos y todas las teselas del área, informa el progreso y, cuando no hay internet, el `MapView` las usa sin ningún cambio en el código. Antes de descargar, `domain/MapasOffline.kt` estima cuántas teselas son (en el zoom z hay 2^z × 2^z teselas en el mundo) para no bajar zonas enormes del servidor público.

## Guía en vivo durante el entrenamiento

- **Hardware (mecanismo 1):** el rumbo del GPS (`Location.bearing`) orienta el mapa al correr; quieto, se usa la brújula (sensor de rotación, `data/ubicacion/Brujula.kt`). Los avisos se dan con el vibrador.
- **Cámara (mecanismo 4):** `ComandoMapa.Seguir` mueve la cámara con rumbo e inclinación 3D; un botón alterna entre modo guía y norte arriba.
- **Cálculo (`domain/Guia.kt`):** la posición se proyecta sobre el segmento más cercano de la ruta para saber los km recorridos y restantes y la distancia a la ruta. En un circuito se busca cerca del último segmento conocido, para no confundir el final con el inicio. El aviso de desvío tiene histéresis: sale a más de 30 m y vuelve a menos de 20 m.
- **Funciona con la pantalla apagada:** la guía corre dentro de la sesión de entrenamiento (servicio en primer plano). La notificación muestra "faltan X km" y el teléfono vibra al desviarse, al volver y al llegar.


