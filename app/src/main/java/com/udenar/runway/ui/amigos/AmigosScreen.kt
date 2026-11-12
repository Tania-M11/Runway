package com.udenar.runway.ui.amigos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.udenar.runway.data.amigos.Amigo
import com.udenar.runway.ui.components.AvatarImagen
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.CampoTexto
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EncabezadoSeccion
import com.udenar.runway.ui.components.EstadoCargando
import com.udenar.runway.ui.components.EstadoVacio
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.components.TipoCampo
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway

/** Amigos: agregar por código (con aceptación), solicitudes y lista. */
@Composable
fun AmigosScreen(
    miCodigo: String,
    onVolver: () -> Unit,
    vm: AmigosViewModel = viewModel(factory = AmigosViewModel.Factory),
) {
    val e by vm.estado.collectAsStateWithLifecycle()
    val foco = LocalFocusManager.current
    var porEliminar by remember { mutableStateOf<Amigo?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = Espacio.margen, end = Espacio.margen, bottom = Espacio.xl),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotonCircular(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", onClick = onVolver)
                Spacer(Modifier.width(Espacio.m))
                EncabezadoPantalla("Amigos", Modifier.weight(1f))
                BotonCircular(Icons.Outlined.Refresh, "Actualizar", onClick = vm::recargar)
            }
        }

        // Agregar con código
        item {
            TarjetaRunway(Modifier.fillMaxWidth()) {
                Text("Agregar amigo", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Escribe el código de 6 caracteres que tu amigo ve en su Perfil. El tuyo es $miCodigo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Runway.TextoSecundario,
                )
                Spacer(Modifier.padding(top = Espacio.s))
                CampoTexto(
                    e.codigo, vm::onCodigo, "Código de tu amigo",
                    tipo = TipoCampo.Correo,
                    ayuda = "${e.codigo.length}/6",
                    onListo = {
                        foco.clearFocus()
                        vm.agregar()
                    },
                )
                BotonRunway(
                    "Enviar solicitud",
                    onClick = {
                        foco.clearFocus()
                        vm.agregar()
                    },
                    icono = Icons.Outlined.PersonAdd,
                    habilitado = e.codigoValido,
                    cargando = e.enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                e.mensaje?.let {
                    Spacer(Modifier.padding(top = Espacio.s))
                    AvisoEstado(if (e.mensajeExito) TipoAviso.Sincronizado else TipoAviso.Error, it)
                }
            }
        }

        item {
            Text(
                "Tus amigos te verán en el mapa solo mientras tengas un entrenamiento activo; al pausar o terminar, desapareces.",
                style = MaterialTheme.typography.bodySmall,
                color = Runway.TextoSecundario,
            )
        }

        if (e.cargando && e.lista.isEmpty()) item { EstadoCargando("Cargando amigos…") }
        e.error?.let { item { AvisoEstado(TipoAviso.SinConexion, it, accion = "Reintentar", onAccion = vm::recargar) } }

        if (e.recibidas.isNotEmpty()) {
            item { EncabezadoSeccion("Solicitudes recibidas (${e.recibidas.size})") }
            items(e.recibidas, key = { it.amistadId }) { a ->
                FilaAmigo(a, "Quiere ser tu amigo") {
                    BotonCircular(Icons.Outlined.Close, "Rechazar", onClick = { vm.responder(a, false) })
                    Spacer(Modifier.width(Espacio.s))
                    BotonCircular(Icons.Outlined.Check, "Aceptar", onClick = { vm.responder(a, true) }, fondo = Runway.Lima)
                }
            }
        }

        item { EncabezadoSeccion("Amigos (${e.amigos.size})") }
        if (!e.cargando && e.amigos.isEmpty()) {
            item {
                EstadoVacio(
                    icono = Icons.Outlined.Groups,
                    titulo = "Aún no tienes amigos en RUNWAY",
                    texto = "Comparte tu código o pide el de alguien para verse en el mapa mientras corren.",
                )
            }
        }
        items(e.amigos, key = { it.amistadId }) { a ->
            FilaAmigo(a, "Amigo") {
                BotonCircular(Icons.Outlined.PersonRemove, "Eliminar amigo", onClick = { porEliminar = a })
            }
        }

        if (e.enviadas.isNotEmpty()) {
            item { EncabezadoSeccion("Solicitudes enviadas") }
            items(e.enviadas, key = { it.amistadId }) { a ->
                FilaAmigo(a, "Esperando que acepte") {
                    TextButton(onClick = { vm.eliminar(a) }) { Text("Cancelar", color = Runway.TextoSecundario) }
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }

    porEliminar?.let { a ->
        AlertDialog(
            onDismissRequest = { porEliminar = null },
            containerColor = Runway.Fondo,
            title = { Text("¿Eliminar a ${a.nombre}?") },
            text = { Text("Dejarán de verse en el mapa. Pueden volver a agregarse con el código.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminar(a)
                    porEliminar = null
                }) { Text("Eliminar", color = Runway.Error) }
            },
            dismissButton = { TextButton(onClick = { porEliminar = null }) { Text("Cancelar", color = Runway.TextoSecundario) } },
        )
    }
}

@Composable
private fun FilaAmigo(a: Amigo, detalle: String, acciones: @Composable () -> Unit) {
    TarjetaRunway(Modifier.fillMaxWidth(), relleno = Espacio.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImagen(a.avatar, tamano = 48.dp)
            Spacer(Modifier.width(Espacio.m))
            Column(Modifier.weight(1f)) {
                Text(a.nombre, style = MaterialTheme.typography.titleSmall)
                Text(detalle, style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario)
            }
            acciones()
        }
    }
}
