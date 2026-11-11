package com.udenar.runway.ui.explorar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.lugares.BuscadorLugares
import com.udenar.runway.data.preferencias.PreferenciasMapa
import com.udenar.runway.data.red.MonitorConexion
import com.udenar.runway.domain.Coordenada
import com.udenar.runway.domain.Lugar
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.ui.mapa.ComandoMapa
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExplorarEstado(
    val camara: PosicionCamara,
    val mapaListo: Boolean = false,
    val errorMapa: Boolean = false,
    val intentoCarga: Int = 0,
    val conectado: Boolean = true,
    val consulta: String = "",
    val buscando: Boolean = false,
    val resultados: List<Lugar> = emptyList(),
    val mensajeBusqueda: String? = null,
    val seleccionado: Lugar? = null,
)

/**
 * ViewModel de Explorar: recibe los eventos del mapa (toques, movimientos, errores),
 * consulta la capa de datos y publica un estado que la pantalla solo dibuja.
 */
class ExplorarViewModel(
    private val buscador: BuscadorLugares,
    private val preferencias: PreferenciasMapa,
    monitor: MonitorConexion,
) : ViewModel() {

    private val _estado = MutableStateFlow(ExplorarEstado(camara = preferencias.ultimaCamara()))
    val estado: StateFlow<ExplorarEstado> = _estado.asStateFlow()

    private val _comandos = Channel<ComandoMapa>(Channel.BUFFERED)
    val comandos = _comandos.receiveAsFlow()

    private var busqueda: Job? = null
    private var nombrePunto: Job? = null

    init {
        viewModelScope.launch {
            monitor.conectado.collect { conectado ->
                val antes = _estado.value
                _estado.update { it.copy(conectado = conectado) }
                // Si el mapa falló por falta de red, se reintenta solo al volver la conexión
                if (conectado && !antes.conectado && antes.errorMapa) reintentarMapa()
            }
        }
    }

    // --- Eventos del mapa ---

    fun onEstiloCargado() = _estado.update { it.copy(mapaListo = true, errorMapa = false) }

    fun onErrorMapa() = _estado.update { it.copy(errorMapa = !it.mapaListo) }

    fun reintentarMapa() = _estado.update {
        it.copy(errorMapa = false, mapaListo = false, intentoCarga = it.intentoCarga + 1)
    }

    fun onCamaraQuieta(camara: PosicionCamara) {
        _estado.update { it.copy(camara = camara) }
        preferencias.guardarCamara(camara)
    }

    fun onToqueMapa(punto: Coordenada) {
        // Un toque con resultados abiertos solo los cierra
        if (_estado.value.resultados.isNotEmpty() || _estado.value.mensajeBusqueda != null) {
            limpiarBusqueda()
            return
        }
        _estado.update { it.copy(seleccionado = Lugar("Punto seleccionado", "Buscando dirección…", punto)) }
        nombrePunto?.cancel()
        nombrePunto = viewModelScope.launch {
            val lugar = runCatching { buscador.nombreDe(punto) }.getOrNull()
            _estado.update { e ->
                if (e.seleccionado?.coordenada != punto) e
                else e.copy(seleccionado = lugar ?: Lugar("Punto seleccionado", punto.texto(), punto))
            }
        }
    }

    fun quitarSeleccion() {
        nombrePunto?.cancel()
        _estado.update { it.copy(seleccionado = null) }
    }

    // --- Buscador de lugares (cualquier ciudad) ---

    fun onConsulta(texto: String) {
        _estado.update { it.copy(consulta = texto) }
        busqueda?.cancel()
        val limpio = texto.trim()
        if (limpio.length < 3) {
            _estado.update { it.copy(resultados = emptyList(), buscando = false, mensajeBusqueda = null) }
            return
        }
        busqueda = viewModelScope.launch {
            delay(450) // espera a que el usuario deje de escribir (no saturar el servicio)
            _estado.update { it.copy(buscando = true, mensajeBusqueda = null) }
            val resultado = runCatching { buscador.buscar(limpio, cerca = _estado.value.camara.centro) }
            _estado.update { e ->
                resultado.fold(
                    onSuccess = { lista ->
                        e.copy(
                            buscando = false,
                            resultados = lista,
                            mensajeBusqueda = if (lista.isEmpty()) "No encontramos «$limpio». Prueba con otro nombre." else null,
                        )
                    },
                    onFailure = {
                        e.copy(
                            buscando = false,
                            resultados = emptyList(),
                            mensajeBusqueda = if (!e.conectado) "Sin conexión: no se puede buscar ahora."
                            else "No se pudo buscar. Intenta de nuevo.",
                        )
                    },
                )
            }
        }
    }

    fun elegirLugar(lugar: Lugar) {
        busqueda?.cancel()
        _estado.update {
            it.copy(seleccionado = lugar, consulta = "", resultados = emptyList(), mensajeBusqueda = null, buscando = false)
        }
        _comandos.trySend(ComandoMapa.MoverCamara(lugar.coordenada, zoom = 15.5))
    }

    fun limpiarBusqueda() {
        busqueda?.cancel()
        _estado.update { it.copy(consulta = "", resultados = emptyList(), mensajeBusqueda = null, buscando = false) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                ExplorarViewModel(c.buscadorLugares, c.preferenciasMapa, c.monitorConexion)
            }
        }
    }
}
