// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {

    fun parseMacBytes(macStr: String): ByteArray? {
        val clean = macStr.replace(":", "").replace("-", "").trim()
        if (clean.length != 12) return null
        return try {
            val bytes = ByteArray(6)
            for (i in 0 until 6) {
                val hex = clean.substring(i * 2, i * 2 + 2)
                bytes[i] = hex.toInt(16).toByte()
            }
            bytes
        } catch (e: Exception) {
            null
        }
    }

    fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        val packet = ByteArray(102)
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
        }
        return packet
    }

    suspend fun sendMagicPacket(host: String, mac: String): Boolean = withContext(Dispatchers.IO) {
        val macBytes = parseMacBytes(mac)
        if (macBytes == null) {
            ConnectionRepository.appendLog("[WARN] MAC inválido para Wake-on-LAN: '$mac'")
            return@withContext false
        }
        val magicBytes = buildMagicPacket(macBytes)

        val destinations = mutableListOf<InetAddress>()
        // 1. Unicast para <host>:9
        try {
            destinations.add(InetAddress.getByName(host))
        } catch (e: Exception) {}

        // 2. Broadcast de sub-rede derivado do host IPv4 (ex: 192.168.1.100 -> 192.168.1.255)
        try {
            val parts = host.split(".")
            if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
                val subnetBcast = "${parts[0]}.${parts[1]}.${parts[2]}.255"
                destinations.add(InetAddress.getByName(subnetBcast))
            }
        } catch (e: Exception) {}

        // 3. Broadcast global 255.255.255.255:9
        try {
            destinations.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {}

        val destStrings = destinations.map { "${it.hostAddress}:9" }.distinct().joinToString(", ")

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                // Enviar 3 pacotes UDP por destino contra perdas em Wi-Fi
                for (repeat in 1..3) {
                    for (dest in destinations) {
                        try {
                            val packet = DatagramPacket(magicBytes, magicBytes.size, dest, 9)
                            socket.send(packet)
                        } catch (e: Exception) {}
                    }
                    delay(50)
                }
            }
            ConnectionRepository.appendLog("[WOL] magic packet enviado pra $mac (destinos: $destStrings)")
            true
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[WARN] Falha ao enviar WoL magic packet: ${e.message}")
            false
        }
    }
}
