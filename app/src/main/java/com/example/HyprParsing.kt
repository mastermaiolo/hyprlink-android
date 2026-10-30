// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.json.JSONArray

data class HyprWorkspace(
    val id: Int,
    val name: String,
    val monitor: String,
    val windows: Int,
    val hasFullscreen: Boolean,
    val lastWindow: String,
    val lastWindowTitle: String
)

data class HyprClient(
    val address: String,
    val title: String,
    val clazz: String,
    val workspaceId: Int,
    val workspaceName: String,
    val floating: Boolean,
    val fullscreen: Boolean,
    val pid: Int,
    val hidden: Boolean,
    val mapped: Boolean,
    val focusHistoryId: Int = -1
)

fun parseWorkspaces(jsonStr: String): List<HyprWorkspace> {
    val list = mutableListOf<HyprWorkspace>()
    if (jsonStr.isBlank()) return list
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                HyprWorkspace(
                    id = obj.optInt("id", 0),
                    name = obj.optString("name", ""),
                    monitor = obj.optString("monitor", ""),
                    windows = obj.optInt("windows", 0),
                    hasFullscreen = obj.optBoolean("hasfullscreen", false),
                    lastWindow = obj.optString("lastwindow", ""),
                    lastWindowTitle = obj.optString("lastwindowtitle", "")
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

fun parseClients(jsonStr: String): List<HyprClient> {
    val list = mutableListOf<HyprClient>()
    if (jsonStr.isBlank()) return list
    try {
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val mapped = obj.optBoolean("mapped", true)
            if (!mapped) continue
            
            val wsObj = obj.optJSONObject("workspace")
            val wsId = wsObj?.optInt("id", 0) ?: 0
            val wsName = wsObj?.optString("name", "") ?: ""
            
            list.add(
                HyprClient(
                    address = obj.optString("address", ""),
                    title = obj.optString("title", ""),
                    clazz = obj.optString("class", ""),
                    workspaceId = wsId,
                    workspaceName = wsName,
                    floating = obj.optBoolean("floating", false),
                    fullscreen = obj.optInt("fullscreen", 0) > 0,
                    pid = obj.optInt("pid", 0),
                    hidden = obj.optBoolean("hidden", false),
                    mapped = mapped,
                    focusHistoryId = obj.optInt("focusHistoryID", obj.optInt("focusHistoryId", -1))
                )
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}
