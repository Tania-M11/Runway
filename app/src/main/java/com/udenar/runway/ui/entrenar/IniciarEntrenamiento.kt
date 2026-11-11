package com.udenar.runway.ui.entrenar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.udenar.runway.RunwayApp
import com.udenar.runway.domain.RutaReferencia
import com.udenar.runway.ui.ubicacion.ResultadoPermiso
import com.udenar.runway.ui.ubicacion.abrirAjustesDeLaApp
import com.udenar.runway.ui.ubicacion.rememberPedirPermisoUbicacion

/**
 * Devuelve una función para empezar a entrenar desde cualquier pantalla (Entrenar,
 * detalle de ruta, Mis rutas). Pide antes los permisos necesarios:
 * - Ubicación (obligatorio: sin GPS no hay registro).
 * - Notificaciones en Android 13+ (opcional: si se niega, igual se registra).
 */
@Composable
fun rememberIniciarEntrenamiento(onIniciado: () -> Unit): (RutaReferencia?) -> Unit {
    val context = LocalContext.current
    val sesion = (context.applicationContext as RunwayApp).contenedor.sesion
    val ubicacion = (context.applicationContext as RunwayApp).contenedor.ubicacion
    val alIniciar by rememberUpdatedState(onIniciado)
    var pendiente by remember { mutableStateOf<Pendiente?>(null) }

    val pedirNotificaciones = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun arrancar(referencia: RutaReferencia?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        sesion.iniciar(referencia)
        alIniciar()
    }

    val pedirUbicacion = rememberPedirPermisoUbicacion { r ->
        val p = pendiente
        pendiente = null
        when (r) {
            ResultadoPermiso.Concedido -> if (p != null) arrancar(p.referencia)
            ResultadoPermiso.Denegado -> Toast.makeText(context, "Sin permiso de ubicación no se puede registrar el recorrido", Toast.LENGTH_LONG).show()
            ResultadoPermiso.DenegadoPermanente -> context.abrirAjustesDeLaApp()
        }
    }

    return { referencia ->
        when {
            sesion.estado.value.enCurso -> alIniciar() // ya hay uno: se abre el que está en curso
            ubicacion.tienePermiso() -> arrancar(referencia)
            else -> {
                pendiente = Pendiente(referencia)
                pedirUbicacion()
            }
        }
    }
}

private data class Pendiente(val referencia: RutaReferencia?)
