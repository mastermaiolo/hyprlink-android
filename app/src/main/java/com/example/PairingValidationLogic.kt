// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

/**
 * Lógica de validação de identidade e requisitos de emparelhamento.
 */
object PairingValidationLogic {

    /**
     * Valida se a identidade local do dispositivo está apta para emparelhamento mTLS.
     * Identidades temporárias em RAM são expressamente proibidas de emparelhar,
     * impedindo que o PC fixe um certificado que deixará de existir no próximo arranque.
     */
    fun canPairWithIdentity(identity: DeviceIdentity?): Boolean {
        if (identity == null) return false
        return !identity.isTemporary
    }
}
