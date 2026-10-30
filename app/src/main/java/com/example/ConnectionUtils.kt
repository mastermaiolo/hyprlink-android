// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import tech.kwik.core.QuicClientConnection
import tech.kwik.core.QuicStream
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.security.MessageDigest

object ConnectionUtils {
    const val HYPRLINK_SERVICE_PORT = 7443

    suspend fun <T> readWithDeadline(stream: QuicStream, timeoutMs: Long = 10_000, block: () -> T): T {
        return try {
            withTimeout(timeoutMs) {
                runInterruptible(Dispatchers.IO) { block() }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            try { stream.abortReading(0) } catch (ignored: Exception) {}
            throw java.io.IOException("Stream read timed out after ${timeoutMs}ms", e)
        }
    }

    fun connectPinned(
        host: String,
        port: Int,
        applicationProtocol: String,
        expectedFingerprint: String,
        identity: DeviceIdentity
    ): QuicClientConnection {
        val quicConn = QuicClientConnection.newBuilder()
            .host(host)
            .port(port)
            .applicationProtocol(applicationProtocol)
            .noServerCertificateCheck()
            .maxOpenPeerInitiatedBidirectionalStreams(100)
            .maxOpenPeerInitiatedUnidirectionalStreams(100)
            .clientCertificate(identity.certificate)
            .clientCertificateKey(identity.privateKey)
            .enableDatagramExtension()
            .defaultStreamReceiveBufferSize(10_000_000L)
            .build()

        // Padrão do Kwik é 250_000 bytes (~244KB) por stream — baixo demais
        // pro audio tap/mic (streams unidirecionais contínuos): qualquer
        // soneca do rádio WiFi (jitter de RTT) atrasa a renovação da janela
        // de flow control o bastante pra travar o stream inteiro. 10MB dá
        // margem confortável mesmo com vários segundos de rede degradada.
        // NOTA: defaultStreamReceiveBufferSize() do builder NÃO cobre uni-streams
        // (bug/limitação do Kwik) — precisa das duas chamadas abaixo, na conexão.
        quicConn.setDefaultUnidirectionalStreamReceiveBufferSize(10_000_000L)
        quicConn.setDefaultBidirectionalStreamReceiveBufferSize(10_000_000L)

        quicConn.connect()

        if (quicConn.isConnected) {
            val chain = quicConn.serverCertificateChain
            if (chain.isNullOrEmpty()) {
                quicConn.close()
                throw SecurityException("Server certificate chain is empty")
            }
            val serverCert = chain[0]
            val md = MessageDigest.getInstance("SHA-256")
            val fingerprintBytes = md.digest(serverCert.encoded)
            val actual = fingerprintBytes.joinToString("") { "%02X".format(it) }

            val actualClean = actual.replace(":", "").uppercase()
            val expectedClean = expectedFingerprint.replace(":", "").uppercase()

            if (actualClean != expectedClean) {
                quicConn.close()
                throw SecurityException("Server fingerprint mismatch — connection closed")
            }
        }
        return quicConn
    }
}
