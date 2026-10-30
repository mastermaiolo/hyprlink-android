// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.tiles

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.ConnectionRepository
import com.example.ConnectionStatus
import com.example.LanguageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CustomShortcutTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val strings = LanguageManager.currentStrings
        tile.label = strings.tileCustomShortcut

        val isConnected = ConnectionRepository.connectionStatus.value == ConnectionStatus.CONNECTED &&
                ConnectionRepository.activeConnection?.isConnected == true

        val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val cmd = prefs.getString("custom_shortcut_cmd", "")?.trim() ?: ""

        if (isConnected && cmd.isNotBlank()) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = cmd
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = if (!isConnected) strings.noConnection else strings.noCommandConfigured
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isConnected = ConnectionRepository.connectionStatus.value == ConnectionStatus.CONNECTED &&
                ConnectionRepository.activeConnection?.isConnected == true

        val prefs = getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        val cmd = prefs.getString("custom_shortcut_cmd", "")?.trim() ?: ""

        if (!isConnected) {
            val strings = LanguageManager.currentStrings
            Toast.makeText(applicationContext, strings.noConnection, Toast.LENGTH_SHORT).show()
            updateTileState()
            return
        }

        if (cmd.isBlank()) {
            val strings = LanguageManager.currentStrings
            Toast.makeText(applicationContext, strings.toastConfigureCommandInSettings, Toast.LENGTH_SHORT).show()
            updateTileState()
            return
        }

        serviceScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    ConnectionRepository.dispatchHyprCommand(cmd)
                }
                if (!ok) {
                    val strings = LanguageManager.currentStrings
                    Toast.makeText(applicationContext, strings.toastShortcutFailed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val strings = LanguageManager.currentStrings
                Toast.makeText(applicationContext, e.message ?: strings.toastShortcutFailed, Toast.LENGTH_SHORT).show()
            }
            updateTileState()
        }
    }
}
