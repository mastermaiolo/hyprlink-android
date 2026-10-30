// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.tiles

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

class SuspendPcTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val strings = LanguageManager.currentStrings
        tile.label = strings.tileSuspendPc

        val isConnected = ConnectionRepository.connectionStatus.value == ConnectionStatus.CONNECTED &&
                ConnectionRepository.activeConnection?.isConnected == true

        if (isConnected) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = strings.linkActive
        } else {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = strings.noConnection
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isConnected = ConnectionRepository.connectionStatus.value == ConnectionStatus.CONNECTED &&
                ConnectionRepository.activeConnection?.isConnected == true

        if (!isConnected) {
            val strings = LanguageManager.currentStrings
            Toast.makeText(applicationContext, strings.noConnection, Toast.LENGTH_SHORT).show()
            updateTileState()
            return
        }

        serviceScope.launch {
            val strings = LanguageManager.currentStrings
            try {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val res = ConnectionRepository.sendPcAction("suspend")
                        if (!res.ok && res.error?.contains("unknown", ignoreCase = true) == true) {
                            val fbOk = ConnectionRepository.dispatchHyprCommand("exec systemctl suspend")
                            com.example.PcActionResult(ok = fbOk, error = if (!fbOk) res.error else null)
                        } else {
                            res
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("unknown", ignoreCase = true) == true) {
                            val fbOk = ConnectionRepository.dispatchHyprCommand("exec systemctl suspend")
                            com.example.PcActionResult(ok = fbOk, error = if (!fbOk) e.message else null)
                        } else {
                            throw e
                        }
                    }
                }
                if (result.ok) {
                    Toast.makeText(applicationContext, strings.actionDone, Toast.LENGTH_SHORT).show()
                } else {
                    val errMsg = result.error ?: strings.toastSuspendPcFailed
                    Toast.makeText(applicationContext, "${strings.actionFailed}: $errMsg", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                val errMsg = e.message ?: strings.toastSuspendPcFailed
                Toast.makeText(applicationContext, "${strings.actionFailed}: $errMsg", Toast.LENGTH_SHORT).show()
            }
            updateTileState()
        }
    }
}
