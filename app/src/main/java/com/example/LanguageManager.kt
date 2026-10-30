// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("", "Padrão do Sistema"),
    PT_BR("pt-BR", "Português (Brasil)"),
    PT_PT("pt-PT", "Português (Portugal)"),
    EN_UK("en-GB", "English (UK)"),
    ES_ES("es-ES", "Español (España)"),
    ZH_CN("zh-CN", "中文 (简体)");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
        }
    }
}

object LanguageManager {
    private const val PREFS_NAME = "hyprlink_language_prefs"
    private const val KEY_LANG = "selected_app_language"

    private val _currentLanguage = mutableStateOf(AppLanguage.SYSTEM)
    val currentLanguage: State<AppLanguage> = _currentLanguage

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCode = prefs.getString(KEY_LANG, "") ?: ""
        _currentLanguage.value = AppLanguage.fromCode(savedCode)
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        _currentLanguage.value = language
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANG, language.code).apply()
    }

    fun getEffectiveLanguageCode(): String {
        val current = _currentLanguage.value
        if (current != AppLanguage.SYSTEM) {
            return current.code
        }
        val defaultLocale = Locale.getDefault()
        val lang = defaultLocale.language
        val country = defaultLocale.country
        return if (country.isNotEmpty()) "$lang-$country" else lang
    }

    val currentStrings: AppStrings
        get() {
            val lang = _currentLanguage.value
            if (lang != AppLanguage.SYSTEM) {
                return getAppStrings(lang)
            }
            val locale = Locale.getDefault()
            val langCode = locale.language.lowercase()
            val countryCode = locale.country.uppercase()
            return when {
                langCode == "pt" && (countryCode == "PT" || countryCode == "AO" || countryCode == "MZ") -> getAppStrings(AppLanguage.PT_PT)
                langCode == "pt" -> getAppStrings(AppLanguage.PT_BR)
                langCode == "es" -> getAppStrings(AppLanguage.ES_ES)
                langCode == "zh" -> getAppStrings(AppLanguage.ZH_CN)
                langCode == "en" -> getAppStrings(AppLanguage.EN_UK)
                else -> getAppStrings(AppLanguage.EN_UK)
            }
        }
}

val LocalAppStrings = staticCompositionLocalOf { getAppStrings(AppLanguage.PT_BR) }

@Composable
fun rememberAppStrings(): AppStrings {
    val lang = LanguageManager.currentLanguage.value
    if (lang != AppLanguage.SYSTEM) {
        return getAppStrings(lang)
    }
    val config = LocalConfiguration.current
    val locale = config.locales[0] ?: Locale.getDefault()
    val langCode = locale.language.lowercase()
    val countryCode = locale.country.uppercase()

    return when {
        langCode == "pt" && (countryCode == "PT" || countryCode == "AO" || countryCode == "MZ") -> getAppStrings(AppLanguage.PT_PT)
        langCode == "pt" -> getAppStrings(AppLanguage.PT_BR)
        langCode == "es" -> getAppStrings(AppLanguage.ES_ES)
        langCode == "zh" -> getAppStrings(AppLanguage.ZH_CN)
        langCode == "en" -> getAppStrings(AppLanguage.EN_UK)
        else -> getAppStrings(AppLanguage.EN_UK)
    }
}
