// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import org.junit.Assert.*
import org.junit.Test

class WorkstationSelectionLogicTest {

    private fun createWorkstation(id: String, name: String): PairedWorkstation {
        return PairedWorkstation(
            id = id,
            deviceName = name,
            host = "192.168.1.50",
            port = "7443",
            fingerprint = "AA:BB:CC",
            pairingTokenHex = null,
            pairedAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testLoadingState_nullWorkstations_returnsNoActionAndPreservesStoredId() {
        val result = WorkstationSelectionLogic.resolveActiveWorkstation(
            workstations = null,
            storedActiveId = "saved_station_123"
        )
        assertNull(result.activeWorkstation)
        assertEquals("saved_station_123", result.resolvedId)
        assertFalse("Não deve abrir emparelhamento enquanto carrega", result.shouldOpenPairing)
        assertTrue(result.isLoading)
    }

    @Test
    fun testEmptyWorkstationList_returnsNullAndOpensPairing() {
        val result = WorkstationSelectionLogic.resolveActiveWorkstation(
            workstations = emptyList(),
            storedActiveId = "non_existent_id"
        )
        assertNull(result.activeWorkstation)
        assertEquals("", result.resolvedId)
        assertTrue(result.shouldOpenPairing)
        assertFalse(result.isLoading)
    }

    @Test
    fun testEmptyWorkstationList_withEmptyStoredId_returnsNullAndOpensPairing() {
        val result = WorkstationSelectionLogic.resolveActiveWorkstation(
            workstations = emptyList(),
            storedActiveId = ""
        )
        assertNull(result.activeWorkstation)
        assertEquals("", result.resolvedId)
        assertTrue(result.shouldOpenPairing)
        assertFalse(result.isLoading)
    }

    @Test
    fun testNonExistentIdWithAvailableStations_fallsBackToFirstStation() {
        val ws1 = createWorkstation("ws_1", "PC Escritório")
        val ws2 = createWorkstation("ws_2", "Portátil")
        val stations = listOf(ws1, ws2)

        // Simula id órfão após restauro de backup
        val result = WorkstationSelectionLogic.resolveActiveWorkstation(
            workstations = stations,
            storedActiveId = "ws_restored_orphan_999"
        )

        assertNotNull(result.activeWorkstation)
        assertEquals(ws1.id, result.activeWorkstation?.id)
        assertEquals(ws1.id, result.resolvedId)
        assertFalse(result.shouldOpenPairing)
        assertFalse(result.isLoading)
    }

    @Test
    fun testValidStoredId_selectsMatchingStation_preservesSecondPc() {
        val ws1 = createWorkstation("ws_1", "PC Escritório")
        val ws2 = createWorkstation("ws_2", "Portátil")
        val stations = listOf(ws1, ws2)

        val result = WorkstationSelectionLogic.resolveActiveWorkstation(
            workstations = stations,
            storedActiveId = "ws_2"
        )

        assertNotNull(result.activeWorkstation)
        assertEquals("ws_2", result.activeWorkstation?.id)
        assertEquals("ws_2", result.resolvedId)
        assertFalse(result.shouldOpenPairing)
        assertFalse(result.isLoading)
    }
}
