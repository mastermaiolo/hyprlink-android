// SPDX-License-Identifier: GPL-3.0-or-later
package com.example.find

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.AppStrings
import com.example.LanguageManager
import com.example.getAppStrings
import com.example.ui.kit.HyprButton
import com.example.ui.kit.Kicker
import com.example.ui.theme.Hypr
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PlexMono
import kotlinx.coroutines.launch

/**
 * Ecrã a todo o ecrã exibido quando o telemóvel é procurado pelo PC.
 * Acende o ecrã bloqueado, intercepta botões de volume para cancelar
 * e apresenta o botão ENCONTREI.
 */
class FindPhoneActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fecha automaticamente se a procura terminar por timeout ou paragem pelo PC
        lifecycleScope.launch {
            PhoneFindManager.isFindingFlow.collect { isFinding ->
                if (!isFinding) {
                    finish()
                }
            }
        }

        setContent {
            MyApplicationTheme {
                val currentLang by LanguageManager.currentLanguage
                val strings = getAppStrings(currentLang)

                FindPhoneScreen(
                    strings = strings,
                    onFoundClick = {
                        PhoneFindManager.stop(PhoneFindStopReason.USER, context = this@FindPhoneActivity)
                        finish()
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            PhoneFindManager.stop(PhoneFindStopReason.USER, context = this)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun FindPhoneScreen(
    strings: AppStrings,
    onFoundClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Hypr.Void)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Kicker("HYPRLINK · PHONE FIND")

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings.findPhoneNotificationTitle,
                color = Hypr.Paper,
                fontFamily = PlexMono,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.findPhoneNotificationDesc,
                color = Hypr.Sub,
                fontFamily = PlexMono,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            com.example.ui.kit.HyprButton(
                label = strings.btnFound,
                onClick = onFoundClick,
                kind = com.example.ui.kit.HyprButtonKind.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }
    }
}
