package com.udenar.runway.ui.perfil

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.udenar.runway.RunwayApp
import com.udenar.runway.data.sync.Sincronizador
import com.udenar.runway.ui.components.AvatarImagen
import com.udenar.runway.ui.components.AvisoEstado
import com.udenar.runway.ui.components.TipoAviso
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaAcceso
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import kotlinx.coroutines.launch

/**
 * Perfil básico (especificación 5.12): datos de la cuenta, código de amigo y cerrar sesión.
 * Editar nombre, avatar y preferencias llega en la parte 11.
 */
@Composable
fun PerfilScreen(onAmigos: () -> Unit, onMapasOffline: () -> Unit) {
    val context = LocalContext.current
    val contenedor = (context.applicationContext as RunwayApp).contenedor
    val perfil by contenedor.preferenciasPerfil.perfil.collectAsStateWithLifecycle()
    val sesionEntrenamiento by contenedor.sesion.estado.collectAsStateWithLifecycle()
    val portapapeles = LocalClipboardManager.current
    val alcance = rememberCoroutineScope()
    var confirmarSalida by remember { mutableStateOf(false) }
    var saliendo by remember { mutableStateOf(false) }
    var quedanSinSubir by remember { mutableStateOf(0) }
    val pendientes by contenedor.sincronizador.pendientes.collectAsStateWithLifecycle(0)
    val p = perfil ?: return

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espacio.margen)
            .padding(bottom = Espacio.xl),
        verticalArrangement = Arrangement.spacedBy(Espacio.m),
    ) {
        EncabezadoPantalla("Perfil")
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarImagen(p.avatar, tamano = 88.dp)
            Spacer(Modifier.width(Espacio.l))
            Column {
                Text(p.nombre, style = MaterialTheme.typography.headlineSmall)
                contenedor.cuenta.correoActual()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Runway.TextoSecundario)
                }
            }
        }

        // Código para que los amigos te agreguen (parte 10)
        TarjetaRunway(Modifier.fillMaxWidth(), color = Runway.LavandaClaro) {
            Text("Tu código de amigo", style = MaterialTheme.typography.labelMedium, color = Runway.MoradoOscuro)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.codigo.ifBlank { "------" },
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 4.sp),
                    modifier = Modifier.weight(1f),
                )
                BotonCircular(Icons.Outlined.ContentCopy, "Copiar código", onClick = {
                    portapapeles.setText(AnnotatedString(p.codigo))
                    Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                })
                Spacer(Modifier.width(Espacio.s))
                BotonCircular(Icons.Outlined.Share, "Compartir código", onClick = {
                    val texto = "¡Corre conmigo en RUNWAY! Agrégame con mi código: ${p.codigo}"
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), "Compartir código"),
                    )
                })
            }
            Text(
                "Compártelo con tus amigos para verse en el mapa mientras corren.",
                style = MaterialTheme.typography.bodySmall,
                color = Runway.TextoSecundario,
            )
        }

        TarjetaAcceso(
            titulo = "Amigos",
            descripcion = "Agrega amigos con su código y mírense en el mapa mientras corren",
            icono = Icons.Outlined.Groups,
            onClick = onAmigos,
            fondoIcono = Runway.LimaClaro,
        )

        TarjetaAcceso(
            titulo = "Mapas sin conexión",
            descripcion = "Zonas guardadas en el teléfono para correr sin señal",
            icono = Icons.Outlined.CloudDownload,
            onClick = onMapasOffline,
            fondoIcono = Runway.LavandaClaro,
        )

        TarjetaRunway(Modifier.fillMaxWidth()) {
            FilaDato("Nivel", p.nivel?.etiqueta ?: "Sin definir")
            HorizontalDivider(color = Runway.Borde)
            FilaDato("Terreno preferido", p.desnivel?.etiqueta ?: "Sin definir")
        }

        // Estado de sincronización con la nube (offline-first: nunca bloquea el uso)
        if (pendientes > 0) {
            AvisoEstado(
                TipoAviso.PendienteSincronizar,
                if (pendientes == 1) "1 elemento pendiente de sincronizar" else "$pendientes elementos pendientes de sincronizar",
                accion = "Sincronizar",
                onAccion = { Sincronizador.programar(context) },
            )
        } else {
            AvisoEstado(TipoAviso.Sincronizado, "Todo sincronizado con tu cuenta")
        }

        Spacer(Modifier.height(Espacio.s))
        BotonRunway(
            "Cerrar sesión",
            onClick = { confirmarSalida = true },
            cargando = saliendo,
            estilo = EstiloBoton.Contorno,
            icono = Icons.AutoMirrored.Outlined.Logout,
            habilitado = !sesionEntrenamiento.enCurso,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sesionEntrenamiento.enCurso) {
            Text("Finaliza tu entrenamiento antes de cerrar sesión.", style = MaterialTheme.typography.bodySmall, color = Runway.TextoSecundario)
        }
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            containerColor = Runway.Fondo,
            title = { Text("¿Cerrar sesión?") },
            text = {
                Text(
                    "Tus rutas y entrenamientos quedan guardados en este teléfono. " +
                        "Si otra cuenta inicia sesión aquí, se borrarán los que aún no estén sincronizados.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarSalida = false
                    saliendo = true
                    alcance.launch {
                        // Primero se intenta subir lo pendiente con la sesión todavía activa
                        val quedan = contenedor.cuenta.subirAntesDeSalir()
                        saliendo = false
                        if (quedan == 0) contenedor.cuenta.cerrarSesion() else quedanSinSubir = quedan
                    }
                }) { Text("Cerrar sesión", color = Runway.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar", color = Runway.TextoSecundario) } },
        )
    }
    if (quedanSinSubir > 0) {
        DialogoPendientes(
            cantidad = quedanSinSubir,
            onSalir = {
                quedanSinSubir = 0
                alcance.launch { contenedor.cuenta.cerrarSesion() }
            },
            onCancelar = { quedanSinSubir = 0 },
        )
    }
}

@Composable
private fun DialogoPendientes(cantidad: Int, onSalir: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Runway.Fondo,
        title = { Text("Hay datos sin sincronizar") },
        text = {
            Text(
                "Sin conexión no pudimos subir $cantidad elemento(s). Si sales, se subirán cuando vuelvas a entrar " +
                    "con esta cuenta en este teléfono; si entra otra cuenta, se perderán. Te recomendamos conectarte primero.",
            )
        },
        confirmButton = { TextButton(onClick = onSalir) { Text("Salir igual", color = Runway.Error) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = Runway.TextoSecundario) } },
    )
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Espacio.m)) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = Runway.TextoSecundario, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.titleSmall)
    }
}
