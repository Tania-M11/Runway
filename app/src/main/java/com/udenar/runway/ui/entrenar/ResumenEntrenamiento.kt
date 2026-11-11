package com.udenar.runway.ui.entrenar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.local.RepositorioEntrenamientos
import com.udenar.runway.data.local.RepositorioRutas
import com.udenar.runway.domain.Entrenamiento
import com.udenar.runway.domain.Modalidad
import com.udenar.runway.domain.PosicionCamara
import com.udenar.runway.domain.Recomendador
import com.udenar.runway.domain.RutaCandidata
import com.udenar.runway.domain.Visibilidad
import com.udenar.runway.domain.desnivelAcumulado
import com.udenar.runway.domain.distanciaKm
import com.udenar.runway.domain.formatoCronometro
import com.udenar.runway.domain.formatoKm
import com.udenar.runway.domain.formatoMetros
import com.udenar.runway.domain.formatoRitmo
import com.udenar.runway.domain.Nivel
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EstadoCargando
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaMetrica
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.mapa.ComandoMapa
import com.udenar.runway.ui.mapa.MapaRunway
import com.udenar.runway.ui.mapa.TrazoRuta
import com.udenar.runway.ui.navegacion.RutaResumenEntrenamiento
import com.udenar.runway.ui.rutas.HojaGuardarRuta
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

data class ResumenEstado(
    val recienTerminado: Boolean = true,
    val entrenamiento: Entrenamiento? = null,
    val guardadaComo: Visibilidad? = null,
    val guardando: Boolean = false,
)

class ResumenViewModel(
    private val id: String,
    recienTerminado: Boolean,
    private val entrenamientos: RepositorioEntrenamientos,
    private val rutas: RepositorioRutas,
    private val nivel: Nivel,
) : ViewModel() {

    private val _estado = MutableStateFlow(ResumenEstado(recienTerminado = recienTerminado))
    val estado: StateFlow<ResumenEstado> = _estado.asStateFlow()

    private val _comandos = Channel<ComandoMapa>(Channel.CONFLATED)
    val comandosMapa = _comandos.receiveAsFlow()

    init {
        viewModelScope.launch { _estado.update { it.copy(entrenamiento = entrenamientos.obtener(id)) } }
    }

    fun encuadrar() {
        _estado.value.entrenamiento?.puntos?.takeIf { it.isNotEmpty() }?.let { _comandos.trySend(ComandoMapa.Encuadrar(it)) }
    }

    /**
     * Guarda el recorrido realizado como ruta de Mis rutas. El entrenamiento (registro con
     * tiempos) y la ruta (plantilla para repetir) son cosas distintas (especificación 5.9).
     */
    fun guardarComoRuta(nombre: String, visibilidad: Visibilidad) {
        val e = _estado.value.entrenamiento ?: return
        if (e.puntos.size < 2 || _estado.value.guardando) return
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val distancia = e.distanciaM / 1000
            val (subida, bajada) = desnivelAcumulado(e.altitudes, umbralM = 5.0)
            val circular = distanciaKm(e.puntos.first(), e.puntos.last()) < 0.15
            val ruta = RutaCandidata(
                id = e.id,
                nombre = nombre,
                puntos = e.puntos,
                distanciaKm = distancia,
                subidaM = subida,
                bajadaM = bajada,
                elevaciones = e.altitudes,
                intervaloElevacionM = 0.0,
                calles = emptyList(),
                giros = 0,
                modalidad = if (circular) Modalidad.Circular else Modalidad.Lineal,
                tiempoMin = Recomendador.tiempoMin(distancia, subida, nivel),
                exigencia = Recomendador.exigencia(distancia, subida, nivel),
                puntuacion = 0.0,
            )
            rutas.guardar(ruta, nombre, visibilidad, inicioNombre = "Inicio del recorrido", finNombre = if (circular) null else "Fin del recorrido")
            _estado.update { it.copy(guardando = false, guardadaComo = visibilidad) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                val ruta = createSavedStateHandle().toRoute<RutaResumenEntrenamiento>()
                ResumenViewModel(
                    ruta.id,
                    ruta.recienTerminado,
                    c.entrenamientos,
                    c.rutas,
                    c.preferenciasPerfil.perfil.value?.nivel ?: Nivel.Intermedio,
                )
            }
        }
    }
}

/** Resumen del entrenamiento recién completado (especificación 5.9). */
@Composable
fun ResumenEntrenamientoScreen(
    onListo: () -> Unit,
    vm: ResumenViewModel = viewModel(factory = ResumenViewModel.Factory),
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val ent = e.entrenamiento ?: run {
        EstadoCargando("Preparando tu resumen…", Modifier.fillMaxSize())
        return
    }
    var mostrarGuardar by remember { mutableStateOf(false) }
    LaunchedEffect(e.guardadaComo) { if (e.guardadaComo != null) mostrarGuardar = false }
    val camara = remember { PosicionCamara(ent.puntos.firstOrNull() ?: com.udenar.runway.domain.CAMARA_POR_DEFECTO.centro, 15.0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            MapaRunway(
                camaraInicial = camara,
                puntoSeleccionado = ent.puntos.firstOrNull(),
                finRuta = ent.puntos.lastOrNull(),
                rutas = ent.segmentos.filter { it.size > 1 }.mapIndexed { i, t -> TrazoRuta("t$i", t, "#7668E8", true) },
                comandos = vm.comandosMapa,
                onEstiloCargado = vm::encuadrar,
                modifier = Modifier.fillMaxSize(),
            )
            BotonCircular(
                Icons.Outlined.Close, "Cerrar resumen", onClick = onListo, sombra = 4.dp,
                modifier = Modifier.statusBarsPadding().padding(Espacio.l),
            )
        }
        Column(Modifier.padding(horizontal = Espacio.margen).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            Spacer(Modifier.height(Espacio.s))
            if (e.recienTerminado) Text("¡Buen trabajo!", style = MaterialTheme.typography.labelLarge, color = Runway.MoradoOscuro)
            Text(ent.rutaNombre ?: "Carrera libre", style = MaterialTheme.typography.headlineSmall)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(ent.inicioEn)),
                style = MaterialTheme.typography.bodyMedium,
                color = Runway.TextoSecundario,
            )
            Spacer(Modifier.height(Espacio.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                TarjetaMetrica("Distancia", formatoKm(ent.distanciaM / 1000), "km", Icons.Outlined.Straighten,
                    fondo = Runway.LimaClaro, acento = Runway.MoradoOscuro, modifier = Modifier.weight(1f))
                TarjetaMetrica("Duración", formatoCronometro(ent.duracionMs), "", Icons.Outlined.Schedule,
                    fondo = Runway.CelesteClaro, acento = Runway.Celeste, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Espacio.s)) {
                TarjetaMetrica("Ritmo promedio", formatoRitmo(ent.ritmoPromedioSegKm), "/km", Icons.Outlined.Speed,
                    fondo = Runway.LavandaClaro, acento = Runway.Morado, modifier = Modifier.weight(1f))
                TarjetaMetrica("Subida", formatoMetros(ent.subidaM), "m", Icons.AutoMirrored.Outlined.TrendingUp,
                    fondo = Runway.RosaClaro, acento = Runway.Rosa, modifier = Modifier.weight(1f))
            }
            // Estado de guardado y sincronización (la sincronización con Supabase llega en la parte 8)
            AvisoEstado(
                if (ent.pendienteSync) TipoAviso.PendienteSincronizar else TipoAviso.Sincronizado,
                if (ent.pendienteSync) "Guardado en el teléfono · pendiente de sincronizar" else "Guardado y sincronizado",
            )
            Spacer(Modifier.height(Espacio.m))

            if (e.guardadaComo != null) {
                TarjetaRunway(Modifier.fillMaxWidth(), color = Runway.LimaClaro, relleno = Espacio.l) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Runway.Texto)
                        Spacer(Modifier.width(Espacio.s))
                        Text("Recorrido guardado en Mis rutas como ${e.guardadaComo!!.etiqueta.lowercase()}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            } else if (ent.puntos.size >= 2) {
                BotonRunway(
                    "Guardar recorrido en Mis rutas",
                    onClick = { mostrarGuardar = true },
                    estilo = EstiloBoton.Contorno,
                    icono = Icons.Outlined.BookmarkAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BotonRunway(if (e.recienTerminado) "Listo" else "Volver al historial", onClick = onListo, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Espacio.xl))
        }
    }

    if (mostrarGuardar) {
        HojaGuardarRuta(
            nombreInicial = ent.rutaNombre ?: "Mi recorrido",
            guardando = e.guardando,
            onGuardar = vm::guardarComoRuta,
            onCerrar = { mostrarGuardar = false },
        )
    }
}
