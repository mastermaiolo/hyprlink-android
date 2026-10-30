// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

/**
 * Endereço do PC anunciado no core.hello ou pc.addrs.
 */
data class PcAddr(
    val ip: String,
    val kind: String, // "wifi" | "usb" | "eth" | "other"
    val iface: String
)

enum class LinkPathType {
    USB,
    WIFI,
    ETH,
    OTHER;

    companion object {
        fun fromKind(kind: String?): LinkPathType {
            return when (kind?.lowercase()?.trim()) {
                "usb" -> USB
                "wifi" -> WIFI
                "eth" -> ETH
                else -> OTHER
            }
        }
    }
}

/**
 * Decisão de rota de ligação QUIC.
 */
data class ConnectionRouteDecision(
    val targetIp: String,
    val pathType: LinkPathType,
    val isUsbPreferred: Boolean,
    val reason: String
)

object UsbConnectionLogic {

    /**
     * Extrai a lista de PcAddr a partir de uma estrutura de dados de mapa/lista (como do packet.body).
     */
    fun parsePcAddrs(raw: Any?): List<PcAddr> {
        if (raw == null) return emptyList()

        val list = when (raw) {
            is List<*> -> raw
            is Map<*, *> -> raw["pc_addrs"] as? List<*>
            else -> null
        } ?: return emptyList()

        val result = mutableListOf<PcAddr>()
        for (item in list) {
            if (item is Map<*, *>) {
                val ip = (item["ip"] as? String)?.trim()
                val kind = (item["kind"] as? String)?.trim() ?: "other"
                val iface = (item["iface"] as? String)?.trim() ?: ""
                if (!ip.isNullOrBlank()) {
                    result.add(PcAddr(ip = ip, kind = kind, iface = iface))
                }
            }
        }
        return result
    }

    /**
     * Decide o caminho prioritário de ligação.
     * Se a rede USB estiver ativa no telemóvel e existir um endereço USB do PC anunciado,
     * tenta primeiro a ligação USB. Caso contrário, mantém o IP habitual (Wi-Fi/último IP).
     */
    fun selectConnectionRoute(
        availablePcAddrs: List<PcAddr>,
        isUsbTetherActive: Boolean,
        fallbackIp: String
    ): ConnectionRouteDecision {
        if (isUsbTetherActive) {
            val usbAddr = availablePcAddrs.firstOrNull { it.kind.equals("usb", ignoreCase = true) }
            if (usbAddr != null) {
                return ConnectionRouteDecision(
                    targetIp = usbAddr.ip,
                    pathType = LinkPathType.USB,
                    isUsbPreferred = true,
                    reason = "Ancoragem USB ativa e endereço USB do PC disponível (${usbAddr.ip})"
                )
            }
        }

        // Caso a ancoragem não esteja ativa ou o PC não tenha endereço USB anunciado:
        // Se houver fallbackIp válido, usa-o
        val targetIp = fallbackIp.trim()
        val matchingAddr = availablePcAddrs.firstOrNull { it.ip.equals(targetIp, ignoreCase = true) }
        val pathType = if (matchingAddr != null) {
            LinkPathType.fromKind(matchingAddr.kind)
        } else {
            LinkPathType.WIFI
        }

        return ConnectionRouteDecision(
            targetIp = targetIp,
            pathType = pathType,
            isUsbPreferred = false,
            reason = if (isUsbTetherActive) {
                "Ancoragem USB ativa mas sem IP USB anunciado pelo PC; a usar caminho padrão ($targetIp)"
            } else {
                "Ligação padrão Wi-Fi/Rede ($targetIp)"
            }
        )
    }

    /**
     * Avalia se a transição entre dois caminhos justifica a comutação de rota.
     */
    fun shouldSwitchPath(
        currentPath: LinkPathType,
        isUsbTetherActive: Boolean,
        availablePcAddrs: List<PcAddr>
    ): Boolean {
        val hasUsbAddr = availablePcAddrs.any { it.kind.equals("usb", ignoreCase = true) }
        return when (currentPath) {
            LinkPathType.USB -> {
                // Se estávamos em USB mas a ancoragem caiu ou o PC perdeu o IP USB -> comuta de volta
                !isUsbTetherActive || !hasUsbAddr
            }
            else -> {
                // Se estávamos em Wi-Fi/Outro e agora a USB ficou disponível -> comuta para USB
                isUsbTetherActive && hasUsbAddr
            }
        }
    }
}
