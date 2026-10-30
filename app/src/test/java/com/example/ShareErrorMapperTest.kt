// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareErrorMapperTest {

    private val ptStrings = StringsPtPt()

    @Test
    fun testMapShareError_limitExceeded() {
        val result = ShareErrorMapper.mapShareError("excede o limite de 4294967296 bytes", ptStrings)
        assertEquals("Ficheiro excede o limite permitido pelo PC", result)
    }

    @Test
    fun testMapShareError_noSpaceLeft() {
        val result = ShareErrorMapper.mapShareError("sem espaço no destino (1048576 bytes livres)", ptStrings)
        assertEquals("Sem espaço livre no disco do PC", result)
    }

    @Test
    fun testMapShareError_destinationUnavailable() {
        val result = ShareErrorMapper.mapShareError("pasta de destino indisponível", ptStrings)
        assertEquals("Pasta de destino indisponível no PC", result)
    }

    @Test
    fun testMapShareError_cannotCreateFile() {
        val result = ShareErrorMapper.mapShareError("não foi possível criar o ficheiro: Permission denied", ptStrings)
        assertEquals("Não foi possível criar o ficheiro no PC", result)
    }

    @Test
    fun testMapShareError_cancelledByUser() {
        val result = ShareErrorMapper.mapShareError("cancelado pelo utilizador", ptStrings)
        assertEquals("Transferência cancelada no PC", result)
    }

    @Test
    fun testMapShareError_sizeExceededAnnounced() {
        val result = ShareErrorMapper.mapShareError("excedeu o tamanho anunciado", ptStrings)
        assertEquals("Ficheiro excedeu o tamanho anunciado", result)
    }

    @Test
    fun testMapShareError_incompleteTransfer() {
        val result = ShareErrorMapper.mapShareError("incompleto: 1024 de 4096 bytes", ptStrings)
        assertEquals("Incompleto: 1024 de 4096 bytes", result)
    }

    @Test
    fun testMapShareError_connectionBroken() {
        val result = ShareErrorMapper.mapShareError("a ligação falhou a meio", ptStrings)
        assertEquals("A ligação com o PC falhou a meio", result)
    }

    @Test
    fun testMapShareError_diskWriteError() {
        val result = ShareErrorMapper.mapShareError("erro ao escrever no disco", ptStrings)
        assertEquals("Erro ao escrever no disco do PC", result)
    }

    @Test
    fun testMapShareError_timeoutWithoutData() {
        val result = ShareErrorMapper.mapShareError("sem dados há demasiado tempo", ptStrings)
        assertEquals("Tempo esgotado (sem dados há mais de 60 s)", result)
    }

    @Test
    fun testMapShareError_cannotFinishFile() {
        val result = ShareErrorMapper.mapShareError("não foi possível gravar: Read-only file system", ptStrings)
        assertEquals("Não foi possível gravar o ficheiro no PC", result)
    }

    @Test
    fun testMapShareError_unknownReason_fallsBackToDefault() {
        val result1 = ShareErrorMapper.mapShareError("erro inesperado do subsistema xyz", ptStrings)
        assertEquals("O PC recusou o ficheiro", result1)

        val result2 = ShareErrorMapper.mapShareError(null, ptStrings)
        assertEquals("O PC recusou o ficheiro", result2)

        val result3 = ShareErrorMapper.mapShareError("   ", ptStrings)
        assertEquals("O PC recusou o ficheiro", result3)
    }

    @Test
    fun testFormatSavedOnPcAs() {
        val formatted = ShareErrorMapper.formatSavedOnPcAs("foto (1).jpg", ptStrings)
        assertEquals("Guardado no PC como foto (1).jpg", formatted)
    }
}
