package com.udenar.runway.ui.perfil

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.cuenta.RepositorioCuenta
import com.udenar.runway.data.cuenta.ValidacionCuenta
import com.udenar.runway.data.preferencias.PreferenciasPerfil
import com.udenar.runway.domain.Nivel
import com.udenar.runway.domain.PreferenciaDesnivel
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.CampoTexto
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.SelectorAvatar
import com.udenar.runway.ui.components.TarjetaOpcion
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.theme.Espacio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditarPerfilEstado(
    val nombre: String = "",
    val avatar: String = "avatar_1",
    val nivel: Nivel = Nivel.Intermedio,
    val desnivel: PreferenciaDesnivel = PreferenciaDesnivel.Moderado,
    val guardando: Boolean = false,
    /** null = sin guardar; true = guardado en la cuenta; false = guardado solo en el teléfono. */
    val subido: Boolean? = null,
    val cambios: Boolean = false,
) {
    val errorNombre: String? get() = ValidacionCuenta.nombre(nombre)
}

class EditarPerfilViewModel(private val cuenta: RepositorioCuenta, perfiles: PreferenciasPerfil) : ViewModel() {

    private val _estado = MutableStateFlow(
        perfiles.perfil.value.let { p ->
            EditarPerfilEstado(
                nombre = p?.nombre.orEmpty(),
                avatar = p?.avatar ?: "avatar_1",
                nivel = p?.nivel ?: Nivel.Intermedio,
                desnivel = p?.desnivel ?: PreferenciaDesnivel.Moderado,
            )
        },
    )
    val estado: StateFlow<EditarPerfilEstado> = _estado.asStateFlow()

    fun onNombre(v: String) = _estado.update { it.copy(nombre = v.take(30), cambios = true, subido = null) }
    fun onAvatar(v: String) = _estado.update { it.copy(avatar = v, cambios = true, subido = null) }
    fun onNivel(v: Nivel) = _estado.update { it.copy(nivel = v, cambios = true, subido = null) }
    fun onDesnivel(v: PreferenciaDesnivel) = _estado.update { it.copy(desnivel = v, cambios = true, subido = null) }

    fun guardar() {
        val e = _estado.value
        if (e.errorNombre != null || e.guardando) return
        _estado.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val subido = cuenta.editarPerfil(e.nombre, e.avatar, e.nivel, e.desnivel)
            _estado.update { it.copy(guardando = false, subido = subido, cambios = false) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as RunwayApp).contenedor
                EditarPerfilViewModel(c.cuenta, c.preferenciasPerfil)
            }
        }
    }
}

/** Editar perfil (especificación 5.12): nombre, avatar, nivel y preferencia de desnivel. */
@Composable
fun EditarPerfilScreen(
    onVolver: () -> Unit,
    vm: EditarPerfilViewModel = viewModel(factory = EditarPerfilViewModel.Factory),
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    // Guardado en la cuenta: confirmación breve y vuelta al perfil
    LaunchedEffect(e.subido) {
        if (e.subido == true) {
            kotlinx.coroutines.delay(900)
            onVolver()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Espacio.margen)) {
            BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = onVolver)
            Spacer(Modifier.width(Espacio.m))
            EncabezadoPantalla("Editar perfil", Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Espacio.margen),
            verticalArrangement = Arrangement.spacedBy(Espacio.m),
        ) {
            Text("Avatar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Espacio.m))
            SelectorAvatar(e.avatar, vm::onAvatar)
            CampoTexto(e.nombre, vm::onNombre, "Nombre o alias", error = e.errorNombre, ayuda = "${e.nombre.length}/30")

            Text("Nivel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Espacio.s))
            Nivel.entries.forEach { n -> TarjetaOpcion(n.etiqueta, n.descripcion, e.nivel == n, onClick = { vm.onNivel(n) }) }

            Text("Terreno preferido", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Espacio.s))
            PreferenciaDesnivel.entries.forEach { d -> TarjetaOpcion(d.etiqueta, d.descripcion, e.desnivel == d, onClick = { vm.onDesnivel(d) }) }
            Text(
                "El nivel y el terreno ajustan la distancia sugerida, la exigencia y las rutas que te proponemos.",
                style = MaterialTheme.typography.bodySmall,
                color = com.udenar.runway.ui.theme.Runway.TextoSecundario,
            )
            Spacer(Modifier.height(Espacio.s))
        }
        Column(Modifier.padding(horizontal = Espacio.margen, vertical = Espacio.m), verticalArrangement = Arrangement.spacedBy(Espacio.s)) {
            when (e.subido) {
                true -> AvisoEstado(TipoAviso.Sincronizado, "Cambios guardados")
                false -> AvisoEstado(TipoAviso.PendienteSincronizar, "Guardado en el teléfono · se subirá a tu cuenta cuando haya conexión")
                null -> Unit
            }
            BotonRunway(
                "Guardar cambios",
                onClick = {
                    foco.clearFocus()
                    vm.guardar()
                },
                icono = Icons.Outlined.Check,
                habilitado = e.cambios && e.errorNombre == null,
                cargando = e.guardando,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
