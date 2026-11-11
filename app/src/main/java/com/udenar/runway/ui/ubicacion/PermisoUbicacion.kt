package com.udenar.runway.ui.ubicacion

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.app.ActivityCompat

enum class ResultadoPermiso { Concedido, Denegado, DenegadoPermanente }

private val PERMISOS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Devuelve una función que muestra el diálogo del sistema para el permiso de ubicación.
 * Si el usuario ya lo negó con "No volver a preguntar", Android no vuelve a mostrar el
 * diálogo: en ese caso se informa [ResultadoPermiso.DenegadoPermanente] para ofrecer Ajustes.
 */
@Composable
fun rememberPedirPermisoUbicacion(onResultado: (ResultadoPermiso) -> Unit): () -> Unit {
    val actividad: Activity? = LocalActivity.current
    val alResultado by rememberUpdatedState(onResultado)
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val concedido = r.values.any { it }
        val resultado = when {
            concedido -> ResultadoPermiso.Concedido
            actividad != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                actividad, Manifest.permission.ACCESS_FINE_LOCATION,
            ) -> ResultadoPermiso.DenegadoPermanente
            else -> ResultadoPermiso.Denegado
        }
        alResultado(resultado)
    }
    return { lanzador.launch(PERMISOS) }
}

/** Abre la ficha de la app en Ajustes para conceder el permiso a mano. */
fun Context.abrirAjustesDeLaApp() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** Abre el ajuste del sistema para encender la ubicación (GPS). */
fun Context.abrirAjustesDeUbicacion() {
    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
