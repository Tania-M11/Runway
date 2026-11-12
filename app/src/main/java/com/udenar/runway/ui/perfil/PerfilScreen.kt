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
import com.udenar.runway.ui.components.AvatarImagen
import com.udenar.runway.ui.components.BotonCircular
import com.udenar.runway.ui.components.BotonRunway
import com.udenar.runway.ui.components.EncabezadoPantalla
import com.udenar.runway.ui.components.EstiloBoton
import com.udenar.runway.ui.components.TarjetaRunway
import com.udenar.runway.ui.theme.Espacio
import com.udenar.runway.ui.theme.Runway
import kotlinx.coroutines.launch

/**
 * Perfil básico (especificación 5.12): datos de la cuenta, código de amigo y cerrar sesión.
 * Editar nombre, avatar y preferencias llega en la parte 11.
 */
@Composable
fun PerfilScreen() {
    val context = LocalContext.current
    val contenedor = (context.applicationContext as RunwayApp).contenedor
    val perfil by contenedor.preferenciasPerfil.perfil.collectAsStateWithLifecycle()
    val sesionEntrenamiento by contenedor.sesion.estado.collectAsStateWithLifecycle()
    val portapapeles = LocalClipboardManager.current
    val alcance = rememberCoroutineScope()
    var confirmarSalida by remember { mutableStateOf(false) }
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

        TarjetaRunway(Modifier.fillMaxWidth()) {
            FilaDato("Nivel", p.nivel?.etiqueta ?: "Sin definir")
            HorizontalDivider(color = Runway.Borde)
            FilaDato("Terreno preferido", p.desnivel?.etiqueta ?: "Sin definir")
        }

        Spacer(Modifier.height(Espacio.s))
        BotonRunway(
            "Cerrar sesión",
            onClick = { confirmarSalida = true },
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
                    alcance.launch { contenedor.cuenta.cerrarSesion() }
                }) { Text("Cerrar sesión", color = Runway.Error) }
            },
            dismissButton = { TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar", color = Runway.TextoSecundario) } },
        )
    }
}

@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = Espacio.m)) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = Runway.TextoSecundario, modifier = Modifier.weight(1f))
        Text(valor, style = MaterialTheme.typography.titleSmall)
    }
}
