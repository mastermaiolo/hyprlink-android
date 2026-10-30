// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object SupportDonateLogic {

    fun isSupportSectionVisible(donateUrl: String?): Boolean {
        return !donateUrl.isNullOrBlank()
    }

    fun createDonateIntent(donateUrl: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openDonateUrl(
        context: Context,
        donateUrl: String,
        fallbackMessage: String = "Nenhum navegador encontrado para abrir a ligação."
    ): Boolean {
        return try {
            val intent = createDonateIntent(donateUrl)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
            false
        } catch (_: Exception) {
            Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
            false
        }
    }
}
