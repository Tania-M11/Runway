package com.udenar.runway.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.local.RepositorioEntrenamientos
import com.udenar.runway.domain.Barra
import com.udenar.runway.domain.Entrenamiento
import com.udenar.runway.domain.Estadisticas
import com.udenar.runway.domain.PeriodoHistorial
import com.udenar.runway.domain.Records
import com.udenar.runway.domain.ResumenPeriodo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

enum class MedidaGrafica(val etiqueta: String) { Distancia("Distancia"), Tiempo("Tiempo") }

data class HistorialEstado(
    val cargando: Boolean = true,
    val periodo: PeriodoHistorial = PeriodoHistorial.Semana,
    val medida: MedidaGrafica = MedidaGrafica.Distancia,
    val hayAlguno: Boolean = false,
    val entrenamientos: List<Entrenamiento> = emptyList(),
    val barras: List<Barra> = emptyList(),
    val resumen: ResumenPeriodo = ResumenPeriodo(0.0, 0, 0, 0.0),
    /** Los récords son de todo el historial, no solo del periodo. */
    val records: Records = Records(null, null, null),
)

/** Historial y estadísticas (especificación 5.10). Funciona con datos locales aunque no haya sincronización. */
class HistorialViewModel(repositorio: RepositorioEntrenamientos) : ViewModel() {

    private val periodo = MutableStateFlow(PeriodoHistorial.Semana)
    private val medida = MutableStateFlow(MedidaGrafica.Distancia)

    val estado: StateFlow<HistorialEstado> = combine(repositorio.finalizados(), periodo, medida) { todos, p, m ->
        val ahora = System.currentTimeMillis()
        val zona = ZoneId.systemDefault()
        val delPeriodo = Estadisticas.enPeriodo(todos, p, ahora, zona)
        HistorialEstado(
            cargando = false,
            periodo = p,
            medida = m,
            hayAlguno = todos.isNotEmpty(),
            entrenamientos = delPeriodo,
            barras = Estadisticas.barras(todos, p, ahora, zona),
            resumen = Estadisticas.resumen(delPeriodo),
            records = Estadisticas.records(todos),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistorialEstado())

    fun onPeriodo(p: PeriodoHistorial) {
        periodo.value = p
    }

    fun onMedida(m: MedidaGrafica) {
        medida.value = m
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { HistorialViewModel((this[APPLICATION_KEY] as RunwayApp).contenedor.entrenamientos) }
        }
    }
}
