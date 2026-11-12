# Pruebas de campo de RUNWAY

Lista para probar la app en la calle con varias personas. Cada prueba tiene pasos cortos y el resultado esperado; se marca ✅ / ❌ y se anota lo observado en la tabla del final.

## Preparación

- **Teléfonos:** al menos 2 (ideal 3) con Android 8 o superior, la app instalada desde Android Studio (o el APK de `app/build/outputs/apk/debug/`).
- **Cuentas:** una cuenta por persona. Para pruebas usar cuentas de prueba, no la cuenta personal, y descartar los entrenamientos de prueba al terminar.
- **Permisos:** ubicación "precisa" y "mientras se usa la app", y notificaciones. Se revisan en Perfil → Permisos.
- **Batería:** cargada, sin ahorro de energía extremo (puede cortar el GPS con la pantalla apagada).
- **Lugar:** una zona con calles conocidas y cielo abierto (parque, avenida) y otra con edificios o árboles altos para comparar el GPS.

## A. Cuentas y perfil

| # | Pasos | Esperado |
|---|---|---|
| A1 | Registrar una cuenta nueva con nombre, correo, contraseña y avatar | Entra a la configuración inicial y luego a Inicio; en Perfil aparece el código de amigo |
| A2 | Perfil → lápiz → cambiar avatar, nombre, nivel y terreno → Guardar | "Cambios guardados" y vuelve a Perfil con los datos nuevos |
| A3 | Activar modo avión → editar el perfil → Guardar | "Guardado en el teléfono · se subirá…"; Perfil muestra los cambios |
| A4 | Quitar modo avión → cerrar y abrir la app | Los cambios de A3 llegan a la cuenta (los amigos ven el avatar/nombre nuevo) |
| A5 | Cerrar sesión e iniciar con la misma cuenta en otro teléfono | Aparecen el perfil, las rutas reutilizables y el historial de la cuenta |

## B. Amigos y ubicación en vivo

| # | Pasos | Esperado |
|---|---|---|
| B1 | Persona 1 copia su código; persona 2 lo ingresa en Perfil → Amigos | Persona 1 ve la solicitud pendiente |
| B2 | Persona 1 acepta | Ambos se ven en la lista de amigos |
| B3 | Ninguno entrena y se abre Explorar | No aparece la ubicación del amigo |
| B4 | Persona 1 inicia un entrenamiento y camina | Persona 2 ve su avatar moverse en el mapa (Explorar o entrenando) |
| B5 | Persona 1 termina el entrenamiento | Su avatar desaparece del mapa de la persona 2 en pocos segundos |
| B6 | Eliminar al amigo y repetir B4 | Ya no se ve la ubicación |

## C. Planificar ruta

| # | Pasos | Esperado |
|---|---|---|
| C1 | Planificar con salida en la ubicación actual, 3 km, circuito | Varias rutas cerca de 3 km, con desnivel y puntuación; se ven sobre calles reales |
| C2 | Cambiar la salida a otro lugar buscado por nombre (otra ciudad también) | Las rutas se generan allá |
| C3 | Elegir destino con las sugerencias a la distancia pedida | Rutas hacia el destino con alternativas; el pin del destino se ve en el mapa |
| C4 | Elegir destino moviendo el mapa (pin central) | El nombre del lugar aparece debajo del pin |
| C5 | Guardar una ruta como privada y otra como reutilizable | Ambas aparecen en Mis rutas con su filtro |

## D. Mapas sin conexión

| # | Pasos | Esperado |
|---|---|---|
| D1 | Con internet, en el detalle de una ruta → descargar la zona | Barra de progreso hasta 100 %; la zona aparece en Perfil → Mapas sin conexión |
| D2 | Activar modo avión y abrir Explorar sobre esa zona | El mapa se ve completo con calles y nombres |
| D3 | En modo avión, iniciar un entrenamiento con esa ruta | El mapa, la ruta y la posición GPS funcionan sin datos |
| D4 | Mover el mapa fuera de la zona descargada | Fuera de la zona el mapa queda en blanco o con poco detalle (esperado) |
| D5 | Eliminar la zona desde Perfil | Desaparece de la lista y libera espacio |

## E. Entrenamiento y guía en vivo

| # | Pasos | Esperado |
|---|---|---|
| E1 | Iniciar entrenamiento con ruta y caminar/correr sobre ella | El tramo recorrido cambia de color, bajan los km restantes y sube el porcentaje |
| E2 | Alternar modo guía / norte arriba | En modo guía el mapa gira con la dirección de avance y se inclina |
| E3 | Salirse de la ruta unos 40 m a propósito | Aviso "Fuera de ruta" y vibración; el porcentaje no avanza |
| E4 | Volver a la ruta | El aviso desaparece y vibra de nuevo |
| E5 | Apagar la pantalla 5 minutos mientras se corre | La notificación muestra lo que falta; al encender, el trazo no tiene huecos |
| E6 | Llegar al final de la ruta | Aviso de llegada con vibración |
| E7 | Pausar, esperar y reanudar | El tiempo en pausa no cuenta en el ritmo |
| E8 | Cerrar la app desde recientes a mitad del entrenamiento y volver a abrirla | Ofrece continuar el entrenamiento sin perder los puntos |
| E9 | Terminar y revisar el resumen | Distancia, tiempo, ritmo y mapa coherentes con lo recorrido |

## F. GPS y satélites

| # | Pasos | Esperado |
|---|---|---|
| F1 | En Entrenar, a cielo abierto, esperar 1 minuto | Varios satélites "en uso" de varios sistemas (GPS, GLONASS, Galileo, BeiDou) y buena precisión |
| F2 | Repetir entre edificios o bajo árboles | Menos satélites en uso y peor precisión; anotar las cifras |
| F3 | Comparar la distancia de un tramo conocido (p. ej. una cuadra o pista de 400 m) | Diferencia menor al 5 % |

## G. Sincronización

| # | Pasos | Esperado |
|---|---|---|
| G1 | Entrenar con datos móviles apagados | Al terminar, Perfil indica cambios pendientes de sincronizar |
| G2 | Encender los datos | En poco tiempo Perfil indica "Todo sincronizado con tu cuenta" |
| G3 | Iniciar sesión con esa cuenta en otro teléfono | El entrenamiento de G1 aparece en el historial |

## H. Consumo y estabilidad

| # | Pasos | Esperado |
|---|---|---|
| H1 | Anotar la batería al iniciar y al terminar un entrenamiento de 30 min | Anotar el consumo (referencia: menos de 10–15 % en 30 min) |
| H2 | Usar la app con datos lentos o intermitentes | Se muestran mensajes de error con reintento, sin cierres |

## Registro de resultados

| Fecha | Lugar | Teléfono | Prueba | Resultado | Observaciones |
|---|---|---|---|---|---|
| | | | | | |
