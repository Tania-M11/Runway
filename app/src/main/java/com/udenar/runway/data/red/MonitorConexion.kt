package com.udenar.runway.data.red

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Emite true/false según haya internet; sirve para el indicador "Sin conexión". */
class MonitorConexion(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun tieneInternet(caps: NetworkCapabilities?) =
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

    fun hayConexion(): Boolean = tieneInternet(cm.getNetworkCapabilities(cm.activeNetwork))

    val conectado: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(tieneInternet(caps))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(hayConexion())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
