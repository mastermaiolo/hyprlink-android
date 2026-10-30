// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.AppStrings

enum class KnownManufacturer {
    XIAOMI,
    SAMSUNG,
    OPPO,
    NONE
}

object ManufacturerGuidance {

    /**
     * Resolve o fabricante verificando se os pacotes e atividades reais do SO do fabricante existem.
     * Permite injeção da função de verificação para testes unitários JVM puros (sem dependência de Intent).
     */
    fun resolveManufacturer(
        manufacturer: String = Build.MANUFACTURER,
        isPackageAndActivityAvailable: (packageName: String, activityName: String?) -> Boolean
    ): KnownManufacturer {
        val m = manufacturer.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> {
                val isRealMiui = isPackageAndActivityAvailable(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                if (isRealMiui) KnownManufacturer.XIAOMI else KnownManufacturer.NONE
            }
            m.contains("samsung") -> KnownManufacturer.SAMSUNG
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> {
                val hasColorOs = isPackageAndActivityAvailable(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ) || isPackageAndActivityAvailable(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
                if (hasColorOs) KnownManufacturer.OPPO else KnownManufacturer.NONE
            }
            else -> KnownManufacturer.NONE
        }
    }

    fun getDetectedManufacturer(context: Context): KnownManufacturer {
        return resolveManufacturer(Build.MANUFACTURER) { pkg, activity ->
            try {
                val pm = context.packageManager
                val hasPkg = try {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                    true
                } catch (_: Exception) {
                    false
                }
                if (!hasPkg) return@resolveManufacturer false

                if (activity != null) {
                    val intent = Intent().setComponent(ComponentName(pkg, activity))
                    val resolved = pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
                    resolved != null
                } else {
                    true
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun isRestrictedSettingsApplicable(): Boolean {
        return true
    }

    fun getGuideText(strings: AppStrings, context: Context? = null): String {
        val manufacturer = if (context != null) {
            getDetectedManufacturer(context)
        } else {
            val m = Build.MANUFACTURER.lowercase()
            if (m.contains("samsung")) KnownManufacturer.SAMSUNG else KnownManufacturer.NONE
        }

        return when (manufacturer) {
            KnownManufacturer.XIAOMI -> strings.xiaomiAutostartGuide
            KnownManufacturer.SAMSUNG -> strings.samsungAutostartGuide
            KnownManufacturer.OPPO -> strings.oppoAutostartGuide
            KnownManufacturer.NONE -> strings.genericAutostartGuide
        }
    }

    fun openAutostartOrBatterySettings(context: Context) {
        val manufacturer = getDetectedManufacturer(context)
        val intents = when (manufacturer) {
            KnownManufacturer.XIAOMI -> listOf(
                Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
            KnownManufacturer.OPPO -> listOf(
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
            KnownManufacturer.SAMSUNG -> listOf(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
            KnownManufacturer.NONE -> listOf(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
    }
}
