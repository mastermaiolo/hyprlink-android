// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UsbTetherHelper {

    private val _isUsbNetworkActive = MutableStateFlow(false)
    val isUsbNetworkActive: StateFlow<Boolean> = _isUsbNetworkActive.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Inicia a monitorização reactiva de redes USB/Ethernet de ancoragem.
     */
    fun startMonitoring(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        if (networkCallback != null) return

        // Avaliação inicial
        _isUsbNetworkActive.value = checkUsbNetworkAvailable(cm)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isUsbNetworkActive.value = checkUsbNetworkAvailable(cm)
            }

            override fun onLost(network: Network) {
                _isUsbNetworkActive.value = checkUsbNetworkAvailable(cm)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                _isUsbNetworkActive.value = checkUsbNetworkAvailable(cm)
            }
        }

        try {
            cm.registerNetworkCallback(request, callback)
            networkCallback = callback
        } catch (e: Exception) {
            // Em caso de restrição de permissão, mantém consulta estática
            _isUsbNetworkActive.value = checkUsbNetworkAvailable(cm)
        }
    }

    /**
     * Para a monitorização reactiva.
     */
    fun stopMonitoring(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
    }

    /**
     * Verifica se existe alguma rede ativa com transporte USB ou Ethernet associada à ancoragem.
     */
    fun checkUsbNetworkAvailable(cm: ConnectivityManager): Boolean {
        return try {
            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network) ?: continue
                val hasUsb = caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)
                val hasEth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                if (hasUsb || hasEth) {
                    val linkProps = cm.getLinkProperties(network)
                    val iface = linkProps?.interfaceName?.lowercase() ?: ""
                    // Interfaces típicas de ancoragem Android: rndis, usb, eth
                    if (hasUsb || iface.startsWith("rndis") || iface.startsWith("usb") || iface.startsWith("eth")) {
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Abre o ecrã de Definições de ancoragem USB do sistema operacional Android.
     */
    fun openUsbTetherSettings(context: Context): Boolean {
        val intents = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Tenta o próximo fallback
            }
        }
        return false
    }
}
