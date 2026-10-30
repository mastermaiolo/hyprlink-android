// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

class ZxingQrDecoderTest {

    private val writer = QRCodeWriter()

    /**
     * Converte um BitMatrix gerado pelo ZXing num buffer Y (luma) em escala de cinzas.
     * Suporta padding de stride e inversão (negativo).
     */
    private fun matrixToLuma(
        matrix: BitMatrix,
        rowStrideExtra: Int = 0,
        inverted: Boolean = false
    ): Pair<ByteArray, Int> {
        val width = matrix.width
        val height = matrix.height
        val rowStride = width + rowStrideExtra
        val luma = ByteArray(rowStride * height)

        for (y in 0 until height) {
            val rowOffset = y * rowStride
            for (x in 0 until width) {
                val isBlack = matrix.get(x, y)
                val pixel = if (inverted) {
                    if (isBlack) 255.toByte() else 0.toByte()
                } else {
                    if (isBlack) 0.toByte() else 255.toByte()
                }
                luma[rowOffset + x] = pixel
            }
        }
        return Pair(luma, rowStride)
    }

    @Test
    fun testCanonicalPayload_allRotations() {
        val payload = "8F:4E:92:10:BC:5A:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC|192.168.1.100:7443|d8e03f56a14c99e120bc"
        val matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 200, 200)
        val (luma, rowStride) = matrixToLuma(matrix)

        val rotations = listOf(0, 90, 180, 270)
        for (rotation in rotations) {
            val decoded = decodeQrFromLuma(luma, rowStride, matrix.width, matrix.height, rotation)
            assertNotNull("Falha na decodificação com rotação $rotation", decoded)
            assertEquals(payload, decoded)

            val parsed = parsePairingQr(decoded!!)
            assertNotNull("Falha no parsePairingQr para payload decodificado", parsed)
            assertEquals("192.168.1.100", parsed!!.host)
            assertEquals(7443, parsed.port)
            assertEquals("d8e03f56a14c99e120bc", parsed.pairingToken)
        }
    }

    @Test
    fun testJsonPayload_andUriPayload() {
        val jsonPayload = "{\"fingerprint\":\"AA:BB:CC:DD\",\"host\":\"192.168.1.50\",\"port\":7443,\"token\":\"token123\"}"
        val jsonMatrix = writer.encode(jsonPayload, BarcodeFormat.QR_CODE, 180, 180)
        val (jsonLuma, jsonStride) = matrixToLuma(jsonMatrix)
        val jsonDecoded = decodeQrFromLuma(jsonLuma, jsonStride, jsonMatrix.width, jsonMatrix.height, 0)
        assertNotNull("Deve decodificar payload JSON", jsonDecoded)
        assertEquals(jsonPayload, jsonDecoded)

        val uriPayload = "hyprlink://pair?fp=AA:BB:CC:DD&host=192.168.1.60&port=7443&token=tok456"
        val uriMatrix = writer.encode(uriPayload, BarcodeFormat.QR_CODE, 180, 180)
        val (uriLuma, uriStride) = matrixToLuma(uriMatrix)
        val uriDecoded = decodeQrFromLuma(uriLuma, uriStride, uriMatrix.width, uriMatrix.height, 0)
        assertNotNull("Deve decodificar payload URI", uriDecoded)
        assertEquals(uriPayload, uriDecoded)
    }

    @Test
    fun testInvertedQrCode() {
        val payload = "AA:BB:CC|192.168.1.200:7443|secret"
        val matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 150, 150)
        val (luma, rowStride) = matrixToLuma(matrix, inverted = true)

        val decoded = decodeQrFromLuma(luma, rowStride, matrix.width, matrix.height, 0)
        assertNotNull("Deve decodificar QR code invertido (negativo)", decoded)
        assertEquals(payload, decoded)
    }

    @Test
    fun testRowStrideGreaterThanWidth() {
        val payload = "TEST_ROW_STRIDE|192.168.1.1:7443|tok"
        val matrix = writer.encode(payload, BarcodeFormat.QR_CODE, 160, 160)
        val extraPadding = 48
        val (luma, rowStride) = matrixToLuma(matrix, rowStrideExtra = extraPadding)
        assertEquals(matrix.width + extraPadding, rowStride)

        // Rotação 0
        val decoded0 = decodeQrFromLuma(luma, rowStride, matrix.width, matrix.height, 0)
        assertEquals(payload, decoded0)

        // Rotação 90 com rowStride maior que a largura
        val decoded90 = decodeQrFromLuma(luma, rowStride, matrix.width, matrix.height, 90)
        assertEquals(payload, decoded90)
    }

    @Test
    fun testBlankOrNoisyImage_returnsNull() {
        // Imagem em branco (luma 255)
        val blank = ByteArray(200 * 200) { 255.toByte() }
        val decodedBlank = decodeQrFromLuma(blank, 200, 200, 200, 0)
        assertNull("Imagem em branco não deve conter QR", decodedBlank)

        // Ruído alternado (xadrez 1x1 sem estrutura de QR)
        val noise = ByteArray(200 * 200) { i -> if (i % 2 == 0) 0.toByte() else 255.toByte() }
        val decodedNoise = decodeQrFromLuma(noise, 200, 200, 200, 0)
        assertNull("Ruído aleatório não deve decodificar", decodedNoise)

        // Dimensões inválidas
        assertNull(decodeQrFromLuma(ByteArray(0), 0, 0, 0, 0))
    }
}
