package com.udenar.runway.ui.planificar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.local.RepositorioRutas
import com.udenar.runway.data.lugares.BuscadorLugares
import com.udenar.runway.data.preferencias.PreferenciasPerfil
import com.udenar.runway.data.preferencias.PreferenciasPlan
import com.udenar.runway.data.rutas.PlanificadorRutas
import com.udenar.runway.data.rutas.ResultadoPlan
import com.udenar.runway.data.ubicacion.RepositorioUbicacion
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Destinos
import com.udenar.runway.domain.Lugar
import com.udenar.runway.domain.LugarSugerido
import com.udenar.runway.domain.destino
import com.udenar.runway.domain.distanciaKm
import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.ParametrosRuta
import com.udenar.runway.domain.PreferenciaDesnivel
import com.udenar.runway.domain.Recomendador
import com.udenar.runway.domain.RutaCandidata
import com.udenar.runway.domain.Visibilidad
import com.udenar.runway.ui.mapa.ComandoMapa
import com.udenar.runway.ui.navegacion.GrafoPlanificar
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class OrigenInicio(val etiqueta: String) {
    MiUbicacion("Mi ubicación"),
    PuntoMapa("Punto de Explorar"),
    Mapa("Elegir en el mapa"),
    Buscar("Buscar lugar"),
}

/** Cómo se elige el destino (opcional). */
enum class OrigenDestino(val etiqueta: String) {
    Ninguno("Sin destino"),
    Mapa("Elegir en el mapa"),
    Buscar("Buscar lugar"),
}

enum class ObjetivoPunto { Inicio, Destino }

/** Estado del selector de punto con pin fijo en el centro del mapa. */
data class SelectorPunto(
    val objetivo: ObjetivoPunto,
    val centro: Coordenada? = null,
    val nombre: String? = null,
)

enum class AvisoInicio { SinPermiso, UbicacionApagada, SinSenal }

sealed interface EstadoCalculo {
    data object Inactivo : EstadoCalculo
    data class Calculando(val paso: Int, val total: Int) : EstadoCalculo
    data object Listo : EstadoCalculo
    data object SinRutas : EstadoCalculo
    data object Error : EstadoCalculo
}

data class PlanificadorEstado(
    // Punto de inicio
    val origen: OrigenInicio,
    val puntoMapa: Lugar?,
    val inicio: Lugar? = null,
    val ubicandome: Boolean = false,
    val avisoInicio: AvisoInicio? = null,
    val consulta: String = "",
    val buscandoLugar: Boolean = false,
    val lugaresEncontrados: List<Lugar> = emptyList(),
    // Destino (opcional) y sugerencias
    val origenDestino: OrigenDestino = OrigenDestino.Ninguno,
    val destino: Lugar? = null,
    val consultaDestino: String = "",
    val buscandoDestino: Boolean = false,
    val lugaresDestino: List<Lugar> = emptyList(),
    val sugerencias: List<LugarSugerido> = emptyList(),
    val cargandoSugerencias: Boolean = false,
    val selector: SelectorPunto? = null,
    // Preferencias del entrenamiento
    val distanciaKm: Double,
    val modalidad: Modalidad,
    val nivel: Nivel,
    val desnivel: PreferenciaDesnivel,
    // Resultados
    val calculo: EstadoCalculo = EstadoCalculo.Inactivo,
    val rutas: List<RutaCandidata> = emptyList(),
    val seleccionada: String? = null,
    /** Nombre del final de una ruta lineal (se busca al abrir el detalle). */
    val nombreFin: String? = null,
    /** Alternativas ya guardadas en Mis rutas: id de la alternativa → id guardado. */
    val guardadas: Map<String, Visibilidad> = emptyMap(),
    val guardando: Boolean = false,
) {
    val puedeBuscar: Boolean get() = inicio != null && calculo !is EstadoCalculo.Calculando

    /** Radio en línea recta al que conviene buscar destinos para la distancia pedida. */
    val radioIdealKm: Double get() = Destinos.radioIdealKm(distanciaKm, modalidad)

    /** Distancia aproximada del recorrido cuando hay destino (la marca el destino, no el selector). */
    val distanciaConDestinoKm: Double?
        get() {
            val a = inicio?.coordenada ?: return null
            val b = destino?.coordenada ?: return null
            val recta = distanciaKm(a, b) * 1.3
            return if (modalidad == Modalidad.Lineal) recta else recta * 2
        }
    val rutaSeleccionada: RutaCandidata? get() = rutas.firstOrNull { it.id == seleccionada }
}

const val DISTANCIA_MIN_KM = 1.0
const val DISTANCIA_MAX_KM = 42.0

/**
 * ViewModel del flujo Planificar → Resultados → Detalle (especificación 5.4 a 5.6).
 * Vive en el grafo de navegación del planificador, así los parámetros se conservan
 * al volver atrás para modificarlos.
 */
class PlanificadorViewModel(
    argumentos: GrafoPlanificar,
    perfil: PreferenciasPerfil,
    private val preferenciasPlan: PreferenciasPlan,
    private val ubicacion: RepositorioUbicacion,
    private val buscador: BuscadorLugares,
    private val planificador: PlanificadorRutas,
    private val rutasGuardadas: RepositorioRutas,
) : ViewModel() {

    private val puntoRecibido: Lugar? = argumentos.lugar()
    private val destinoRecibido: Lugar? = argumentos.destino()

    private val _estado = MutableStateFlow(
        perfil.perfil.value.let { p ->
            val nivel = p?.nivel ?: Nivel.Intermedio
            PlanificadorEstado(
                origen = if (puntoRecibido != null) OrigenInicio.PuntoMapa else OrigenInicio.MiUbicacion,
                puntoMapa = puntoRecibido,
                inicio = puntoRecibido,
                destino = destinoRecibido,
                distanciaKm = preferenciasPlan.distanciaKm() ?: Recomendador.distanciaSugerida(nivel),
                modalidad = preferenciasPlan.modalidad(),
                nivel = nivel,
                desnivel = p?.desnivel ?: PreferenciaDesnivel.Moderado,
            )
        },
    )
    val estado: StateFlow<PlanificadorEstado> = _estado.asStateFlow()

    private val _comandos = Channel<ComandoMapa>(Channel.CONFLATED)
    val comandosMapa = _comandos.receiveAsFlow()

    private var trabajoUbicacion: Job? = null
    private var trabajoBusqueda: Job? = null
    private var trabajoCalculo: Job? = null

    private var trabajoBusquedaDestino: Job? = null
    private var trabajoNombreCentro: Job? = null

    init {
        if (puntoRecibido == null) usarMiUbicacion()
        // Si se llega con un destino (desde Explorar), lo natural es una ruta lineal hasta él
        if (destinoRecibido != null) _estado.update { it.copy(modalidad = Modalidad.Lineal) }
        // Sugerencias: se recalculan al cambiar el inicio, la distancia o la modalidad
        viewModelScope.launch {
            _estado.map { Triple(it.inicio?.coordenada, it.distanciaKm, it.modalidad) }
                .distinctUntilChanged()
                .collectLatest { (inicio, km, modalidad) ->
                    if (inicio == null) {
                        _estado.update { it.copy(sugerencias = emptyList(), cargandoSugerencias = false) }
                        return@collectLatest
                    }
                    delay(600) // espera a que el usuario termine de ajustar la distancia
                    _estado.update { it.copy(cargandoSugerencias = true) }
                    val radio = Destinos.radioIdealKm(km, modalidad)
                    val lista = runCatching { buscador.lugaresCercanos(inicio, radio * 1.6) }.getOrDefault(emptyList())
                    _estado.update { it.copy(sugerencias = Destinos.filtrar(lista, radio), cargandoSugerencias = false) }
                }
        }
    }

    // ------------------------------------------------------------ Punto de inicio

    fun elegirOrigen(origen: OrigenInicio) {
        when (origen) {
            OrigenInicio.MiUbicacion -> usarMiUbicacion()
            OrigenInicio.PuntoMapa -> _estado.update {
                it.copy(origen = origen, inicio = it.puntoMapa, avisoInicio = null, ubicandome = false)
            }
            OrigenInicio.Buscar -> _estado.update {
                it.copy(origen = origen, inicio = null, avisoInicio = null, ubicandome = false)
            }
            // El inicio se conserva hasta que se confirme un punto en el selector
            OrigenInicio.Mapa -> _estado.update { it.copy(origen = origen, avisoInicio = null, ubicandome = false) }
        }
        if (origen != OrigenInicio.MiUbicacion) trabajoUbicacion?.cancel()
    }

    /** Usa el GPS (mecanismo 1) como punto de inicio. */
    fun usarMiUbicacion() {
        trabajoUbicacion?.cancel()
        _estado.update { it.copy(origen = OrigenInicio.MiUbicacion, inicio = null, avisoInicio = null) }
        when {
            !ubicacion.tienePermiso() -> _estado.update { it.copy(avisoInicio = AvisoInicio.SinPermiso) }
            !ubicacion.ubicacionActivada() -> _estado.update { it.copy(avisoInicio = AvisoInicio.UbicacionApagada) }
            else -> {
                val reciente = ubicacion.ultimaConocida()?.takeIf { System.currentTimeMillis() - it.tiempoMs < 120_000 }
                if (reciente != null) {
                    fijarMiUbicacion(reciente.coordenada)
                } else {
                    _estado.update { it.copy(ubicandome = true) }
                    trabajoUbicacion = viewModelScope.launch {
                        val p = withTimeoutOrNull(20_000) { ubicacion.posiciones().first { it.precisionM <= 100f } }
                            ?: ubicacion.ultimaConocida()
                        if (p != null) fijarMiUbicacion(p.coordenada)
                        else _estado.update { it.copy(ubicandome = false, avisoInicio = AvisoInicio.SinSenal) }
                    }
                }
            }
        }
    }

    private fun fijarMiUbicacion(c: Coordenada) = _estado.update {
        if (it.origen != OrigenInicio.MiUbicacion) it
        else it.copy(inicio = Lugar("Mi ubicación", c.texto(), c), ubicandome = false, avisoInicio = null)
    }

    fun onConsulta(texto: String) {
        _estado.update { it.copy(consulta = texto) }
        trabajoBusqueda?.cancel()
        val limpio = texto.trim()
        if (limpio.length < 3) {
            _estado.update { it.copy(lugaresEncontrados = emptyList(), buscandoLugar = false) }
            return
        }
        trabajoBusqueda = viewModelScope.launch {
            delay(450)
            _estado.update { it.copy(buscandoLugar = true) }
            val cerca = _estado.value.puntoMapa?.coordenada ?: ubicacion.ultimaConocida()?.coordenada
            val lista = runCatching { buscador.buscar(limpio, cerca) }.getOrDefault(emptyList())
            _estado.update { it.copy(lugaresEncontrados = lista, buscandoLugar = false) }
        }
    }

    fun elegirLugarBuscado(lugar: Lugar) {
        trabajoBusqueda?.cancel()
        _estado.update { it.copy(inicio = lugar, consulta = "", lugaresEncontrados = emptyList(), buscandoLugar = false) }
    }

    // ------------------------------------------------------------ Destino y sugerencias

    fun elegirOrigenDestino(o: OrigenDestino) = _estado.update {
        when (o) {
            OrigenDestino.Ninguno -> it.copy(origenDestino = o, destino = null, consultaDestino = "", lugaresDestino = emptyList())
            else -> it.copy(origenDestino = o)
        }
    }

    fun elegirSugerencia(s: LugarSugerido) = _estado.update {
        it.copy(destino = s.lugar, consultaDestino = "", lugaresDestino = emptyList())
    }

    fun quitarDestino() = _estado.update { it.copy(destino = null, origenDestino = OrigenDestino.Ninguno) }

    fun onConsultaDestino(texto: String) {
        _estado.update { it.copy(consultaDestino = texto) }
        trabajoBusquedaDestino?.cancel()
        val limpio = texto.trim()
        if (limpio.length < 3) {
            _estado.update { it.copy(lugaresDestino = emptyList(), buscandoDestino = false) }
            return
        }
        trabajoBusquedaDestino = viewModelScope.launch {
            delay(450)
            _estado.update { it.copy(buscandoDestino = true) }
            val cerca = _estado.value.inicio?.coordenada
            val lista = runCatching { buscador.buscar(limpio, cerca) }.getOrDefault(emptyList())
            _estado.update { it.copy(lugaresDestino = lista, buscandoDestino = false) }
        }
    }

    fun elegirLugarDestino(lugar: Lugar) = _estado.update {
        it.copy(destino = lugar, consultaDestino = "", lugaresDestino = emptyList(), buscandoDestino = false)
    }

    // ------------------------------------------------------------ Selector de punto (pin al centro)

    fun abrirSelector(objetivo: ObjetivoPunto) {
        val e = _estado.value
        val inicial = when (objetivo) {
            ObjetivoPunto.Inicio -> e.inicio?.coordenada
            ObjetivoPunto.Destino -> e.destino?.coordenada
        }
        _estado.update { it.copy(selector = SelectorPunto(objetivo, centro = inicial)) }
    }

    /** Al cargar el mapa del selector: muestra el inicio y el círculo de la distancia objetivo. */
    fun encuadrarSelector() {
        val e = _estado.value
        val s = e.selector ?: return
        val inicio = e.inicio?.coordenada
        val puntos = when {
            s.objetivo == ObjetivoPunto.Destino && inicio != null ->
                listOf(0.0, 90.0, 180.0, 270.0).map { destino(inicio, it, e.radioIdealKm * 1.1) } + inicio
            else -> listOfNotNull(s.centro ?: inicio)
        }
        if (puntos.isNotEmpty()) _comandos.trySend(ComandoMapa.Encuadrar(puntos, margenPx = 80))
    }

    /** La cámara se detuvo: el punto elegido es el centro; se busca su nombre. */
    fun onCentroSelector(c: Coordenada) {
        _estado.update { it.copy(selector = it.selector?.copy(centro = c, nombre = null)) }
        trabajoNombreCentro?.cancel()
        trabajoNombreCentro = viewModelScope.launch {
            delay(500)
            val lugar = runCatching { buscador.nombreDe(c) }.getOrNull()
            _estado.update { e ->
                if (e.selector?.centro != c) e else e.copy(selector = e.selector.copy(nombre = lugar?.nombre ?: c.texto()))
            }
        }
    }

    /** Confirma el punto del centro (o un lugar sugerido tocado en el mapa). */
    fun confirmarSelector(sugeridoId: String? = null) {
        val e = _estado.value
        val s = e.selector ?: return
        val lugar = sugeridoId?.let { id -> e.sugerencias.firstOrNull { idSugerencia(it) == id }?.lugar }
            ?: s.centro?.let { Lugar(s.nombre ?: "Punto elegido", it.texto(), it) }
            ?: return
        _estado.update {
            when (s.objetivo) {
                ObjetivoPunto.Inicio -> it.copy(inicio = lugar, origen = OrigenInicio.Mapa, selector = null)
                ObjetivoPunto.Destino -> it.copy(destino = lugar, origenDestino = OrigenDestino.Mapa, selector = null)
            }
        }
    }

    fun cerrarSelector() = _estado.update { it.copy(selector = null) }

    // ------------------------------------------------------------ Preferencias

    fun onDistancia(km: Double) = _estado.update {
        it.copy(distanciaKm = (Math.round(km * 2) / 2.0).coerceIn(DISTANCIA_MIN_KM, DISTANCIA_MAX_KM))
    }

    fun onModalidad(m: Modalidad) = _estado.update { it.copy(modalidad = m) }
    fun onNivel(n: Nivel) = _estado.update { it.copy(nivel = n) }
    fun onDesnivel(d: PreferenciaDesnivel) = _estado.update { it.copy(desnivel = d) }

    // ------------------------------------------------------------ Cálculo de alternativas

    fun buscarRutas() {
        val e = _estado.value
        val inicio = e.inicio ?: return
        preferenciasPlan.guardar(e.distanciaKm, e.modalidad)
        val parametros = ParametrosRuta(inicio, e.distanciaKm, e.modalidad, e.nivel, e.desnivel, destino = e.destino)
        trabajoCalculo?.cancel()
        _estado.update { it.copy(calculo = EstadoCalculo.Calculando(0, 1), rutas = emptyList(), seleccionada = null, nombreFin = null) }
        trabajoCalculo = viewModelScope.launch {
            val resultado = planificador.generar(parametros) { paso, total ->
                _estado.update { it.copy(calculo = EstadoCalculo.Calculando(paso, total)) }
            }
            _estado.update {
                when (resultado) {
                    is ResultadoPlan.Rutas -> it.copy(
                        calculo = EstadoCalculo.Listo,
                        rutas = resultado.rutas,
                        seleccionada = resultado.rutas.first().id,
                    )
                    ResultadoPlan.SinRutas -> it.copy(calculo = EstadoCalculo.SinRutas)
                    ResultadoPlan.ErrorServicio -> it.copy(calculo = EstadoCalculo.Error)
                }
            }
            if (resultado is ResultadoPlan.Rutas) {
                _comandos.trySend(ComandoMapa.Encuadrar(resultado.rutas.flatMap { it.puntos }))
            }
        }
    }

    fun seleccionar(id: String) = _estado.update { it.copy(seleccionada = id, nombreFin = null) }

    fun encuadrarSeleccionada() {
        _estado.value.rutaSeleccionada?.let { _comandos.trySend(ComandoMapa.Encuadrar(it.puntos)) }
    }

    /** Al abrir el detalle: busca el nombre del final si la ruta es lineal. */
    fun prepararDetalle() {
        val ruta = _estado.value.rutaSeleccionada ?: return
        // Con destino, el final ya tiene nombre
        _estado.value.destino?.let { d ->
            if (ruta.modalidad == Modalidad.Lineal) _estado.update { it.copy(nombreFin = d.nombre) }
            return
        }
        if (ruta.modalidad == Modalidad.Lineal && _estado.value.nombreFin == null) {
            viewModelScope.launch {
                val lugar = runCatching { buscador.nombreDe(ruta.fin) }.getOrNull()
                _estado.update { e ->
                    if (e.seleccionada != ruta.id) e else e.copy(nombreFin = lugar?.nombre ?: ruta.fin.texto())
                }
            }
        }
    }

    /** Guarda la alternativa seleccionada en el catálogo local (flujo 6.3). */
    fun guardarRuta(nombre: String, visibilidad: Visibilidad) {
        val e = _estado.value
        val ruta = e.rutaSeleccionada ?: return
        if (e.guardando) return
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            rutasGuardadas.guardar(
                ruta = ruta,
                nombre = nombre,
                visibilidad = visibilidad,
                inicioNombre = e.inicio?.nombre ?: ruta.inicio.texto(),
                finNombre = if (ruta.modalidad == Modalidad.Lineal) e.nombreFin else null,
            )
            _estado.update { it.copy(guardando = false, guardadas = it.guardadas + (ruta.id to visibilidad)) }
        }
    }

    fun encuadrarTodas() {
        val rutas = _estado.value.rutas
        if (rutas.isNotEmpty()) _comandos.trySend(ComandoMapa.Encuadrar(rutas.flatMap { it.puntos }))
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                val argumentos = createSavedStateHandle().toRoute<GrafoPlanificar>()
                PlanificadorViewModel(
                    argumentos, c.preferenciasPerfil, c.preferenciasPlan, c.ubicacion, c.buscadorLugares, c.planificador, c.rutas,
                )
            }
        }
    }
}

/** Id estable de una sugerencia para identificarla al tocarla en el mapa. */
fun idSugerencia(s: LugarSugerido): String = "sug-${s.lugar.coordenada.lat}-${s.lugar.coordenada.lon}"
