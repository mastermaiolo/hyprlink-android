// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.tiles

import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.AppDatabase
import com.example.ConnectionRepository
import com.example.ConnectionStatus
import com.example.HyprLinkConnectionService
import com.example.LanguageManager
import com.example.PairedWorkstation
import com.example.WakeOnLan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WakePcTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val strings = LanguageManager.currentStrings
        tile.label = strings.tileWakePc

        val isConnected = ConnectionRepository.connectionStatus.value == ConnectionStatus.CONNECTED &&
                ConnectionRepository.activeConnection?.isConnected == true

        serviceScope.launch {
            val hasMac = withContext(Dispatchers.IO) {
                getWorkstationWithMac() != null
            }
            if (!isConnected && hasMac) {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = strings.tileWakePc
            } else {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = if (isConnected) strings.linkActive else "MAC N/A"
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val ws: PairedWorkstation? = withContext(Dispatchers.IO) {
                getWorkstationWithMac()
            }
            if (ws == null || ws.pcMac.isNullOrBlank()) {
                val strings = LanguageManager.currentStrings
                Toast.makeText(applicationContext, strings.macNotConfigured, Toast.LENGTH_SHORT).show()
                updateTileState()
                return@launch
            }

            val mac = ws.pcMac
            val host = ws.host
            ConnectionRepository.appendLog("[WOL] Tile: A enviar Wake-on-LAN para ${ws.deviceName} ($mac)...")
            val sent = withContext(Dispatchers.IO) {
                WakeOnLan.sendMagicPacket(host, mac)
            }
            val strings = LanguageManager.currentStrings
            if (sent) {
                Toast.makeText(applicationContext, strings.toastWolSent, Toast.LENGTH_SHORT).show()
                val intent = Intent(applicationContext, HyprLinkConnectionService::class.java).apply {
                    action = "ACTION_RECONNECT"
                }
                ContextCompat.startForegroundService(applicationContext, intent)
            } else {
                Toast.makeText(applicationContext, strings.toastWolError, Toast.LENGTH_SHORT).show()
            }
            updateTileState()
        }
    }

    private suspend fun getWorkstationWithMac(): PairedWorkstation? {
        val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val activeId = prefs.getString("active_workstation_id", "") ?: ""
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.workstationDao()
        val ws = if (activeId.isNotBlank()) {
            dao.getById(activeId)
        } else {
            try {
                dao.getAll().firstOrNull()?.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
        return if (ws != null && !ws.pcMac.isNullOrBlank()) ws else null
    }
}
