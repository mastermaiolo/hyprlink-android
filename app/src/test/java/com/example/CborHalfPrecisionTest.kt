// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.HalfPrecisionFloat
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CborHalfPrecisionTest {

    @Test
    fun testDecodePcStatusWithHalfPrecisionFloats() {
        // Codifica um pacote "pc.status" com HalfPrecisionFloat para simular a saída do daemon (ciborium)
        val baos = ByteArrayOutputStream()
        val rootMap = co.nstant.`in`.cbor.model.Map()
        rootMap.put(UnicodeString("id"), UnsignedInteger(1))
        rootMap.put(UnicodeString("type"), UnicodeString("pc.status"))

        val bodyMap = co.nstant.`in`.cbor.model.Map()
        // ciborium codifica 51.0 em f16 (0xF9 0x52 0x60) e 43.125 em f16 (0xF9 0x51 0x64)
        bodyMap.put(UnicodeString("gpu_pct"), HalfPrecisionFloat(51.0f))
        bodyMap.put(UnicodeString("cpu_temp_c"), HalfPrecisionFloat(43.125f))
        rootMap.put(UnicodeString("body"), bodyMap)

        CborEncoder(baos).encode(rootMap)
        val bytes = baos.toByteArray()

        // Confirma que os bytes gerados contêm marcadores 0xF9 de float16 / meia precisão
        assertTrue(
            "Os bytes CBOR devem conter marcadores 0xF9 de meia precisão",
            bytes.any { it == 0xF9.toByte() }
        )

        val packet = ConnectionRepository.decodePacket(bytes)
        assertNotNull("O pacote descodificado não deve ser nulo", packet)
        assertEquals("pc.status", packet?.type)
        assertEquals(1L, packet?.id)

        val body = packet?.body
        assertNotNull("O body não deve ser nulo", body)

        val gpuPct = body?.get("gpu_pct")
        val cpuTemp = body?.get("cpu_temp_c")

        assertTrue("gpu_pct deve chegar como um número", gpuPct is Number)
        assertTrue("cpu_temp_c deve chegar como um número", cpuTemp is Number)

        assertEquals(51.0f, (gpuPct as Number).toFloat(), 0.001f)
        assertEquals(43.125f, (cpuTemp as Number).toFloat(), 0.001f)
    }
}
