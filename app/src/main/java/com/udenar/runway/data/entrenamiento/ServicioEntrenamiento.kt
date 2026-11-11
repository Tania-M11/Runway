package com.udenar.runway.data.entrenamiento

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.udenar.runway.MainActivity
import com.udenar.runway.R
import com.udenar.runway.RunwayApp
import com.udenar.runway.domain.EstadoEntrenamiento
import com.udenar.runway.domain.formatoKm
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano de tipo "location".
 *
 * Android detiene el GPS de las apps en segundo plano para ahorrar batería. Mientras este
 * servicio está activo (con su notificación visible), el sistema permite seguir recibiendo
 * posiciones con la pantalla apagada. No calcula nada: la lógica está en [SesionEntrenamiento];
 * el servicio solo mantiene viva la app y muestra el progreso en la notificación.
 */
class ServicioEntrenamiento : Service() {

    private val scope = MainScope()
    private var observador: Job? = null

    private val sesion get() = (application as RunwayApp).contenedor.sesion

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanal()
        try {
            ServiceCompat.startForeground(
                this, ID_NOTIFICACION, notificacion(sesion.estado.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            // Android 12+ puede negar el arranque desde segundo plano: la sesión queda guardada y pausada
            stopSelf()
            return START_NOT_STICKY
        }
        if (observador == null) {
            observador = scope.launch {
                // intent == null: Android reinició el servicio tras cerrar el proceso → retomar desde SQLite
                if (intent == null && !sesion.continuarTrasReinicio()) {
                    detener()
                    return@launch
                }
                sesion.estado
                    .map { Triple(it.estado, (it.distanciaM / 10).toInt(), it.acumuladoMs) } // refresca cada 10 m
                    .distinctUntilChanged()
                    .collect {
                        val s = sesion.estado.value
                        if (s.estado == null) detener()
                        else getSystemService(NotificationManager::class.java).notify(ID_NOTIFICACION, notificacion(s))
                    }
            }
        }
        return START_STICKY
    }

    private fun notificacion(s: EstadoSesion): Notification {
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val activo = s.estado == EstadoEntrenamiento.Activo
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(if (activo) "Entrenamiento en curso" else "Entrenamiento pausado")
            .setContentText("${formatoKm(s.distanciaM / 1000)} km · se guarda en el teléfono")
            .setContentIntent(abrir)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Cronómetro del sistema: avanza solo, sin despertar la app cada segundo
            .setUsesChronometer(activo)
            .setShowWhen(activo)
            .setWhen(System.currentTimeMillis() - s.duracionMs(System.currentTimeMillis()))
            .build()
    }

    private fun crearCanal() {
        val canal = NotificationChannel(CANAL, "Entrenamiento en curso", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Muestra la distancia y el tiempo mientras registras un entrenamiento"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    private fun detener() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CANAL = "entrenamiento"
        const val ID_NOTIFICACION = 7
    }
}
