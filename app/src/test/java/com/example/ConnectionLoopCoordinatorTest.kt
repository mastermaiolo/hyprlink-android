// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ConnectionLoopCoordinatorTest {

    @Test
    fun testSimultaneousCalls_onlyOneJobActive() {
        runBlocking {
            val coordinator = ConnectionLoopCoordinator(this)
            val loopRuns = AtomicInteger(0)

            // Lança 3 chamadas simultâneas de requestLoop
            val jobs = (1..3).map {
                async {
                    coordinator.requestLoop(
                        stationId = "station_1",
                        isConnected = false,
                        loopRunner = { cycleId ->
                            loopRuns.incrementAndGet()
                            delay(200)
                        }
                    )
                }
            }.awaitAll()

            assertEquals(1, coordinator.cycleCounter.get())
            assertNotNull(coordinator.currentJob)
            assertTrue(coordinator.currentJob!!.isActive)
            coordinator.currentJob?.cancelAndJoin()
        }
    }

    @Test
    fun testRestartWithBlockedJob_oldJobTerminatedBeforeNewStarts() {
        runBlocking {
            val coordinator = ConnectionLoopCoordinator(this, joinTimeoutMs = 1000L)
            var oldJobEnded = false
            var previousClosed = false

            // Inicia job antigo bloqueado
            val oldJob = coordinator.requestLoop(
                stationId = "station_1",
                isConnected = false,
                loopRunner = {
                    try {
                        delay(5000)
                    } finally {
                        oldJobEnded = true
                    }
                }
            )
            assertNotNull(oldJob)
            assertTrue(oldJob!!.isActive)
            yield()

            // Reinicia para uma nova estação station_2
            val newJob = coordinator.requestLoop(
                stationId = "station_2",
                isConnected = false,
                onClosePreviousConnection = {
                    previousClosed = true
                },
                loopRunner = {
                    delay(200)
                }
            )

            assertNotNull(newJob)
            assertTrue("Conexão anterior deve ter sido fechada", previousClosed)
            assertTrue("Job antigo deve ter terminado antes do novo assumir", oldJobEnded)
            assertFalse("Job antigo já não deve estar ativo", oldJob.isActive)
            assertEquals(2, coordinator.cycleCounter.get())
            newJob?.cancelAndJoin()
        }
    }

    @Test
    fun testAttemptInProgress_setsImmediateRetryWithoutCancellingJob() {
        runBlocking {
            val coordinator = ConnectionLoopCoordinator(this)

            val job = coordinator.requestLoop(
                stationId = "station_1",
                isConnected = false,
                loopRunner = {
                    delay(1000)
                }
            )
            coordinator.isAttemptInProgress = true

            // Nova chamada para a mesma estação enquanto tentativa está em curso
            val sameJob = coordinator.requestLoop(
                stationId = "station_1",
                isConnected = false,
                loopRunner = {
                    fail("Não deve lançar novo runner")
                }
            )

            assertSame(job, sameJob)
            assertEquals(1, coordinator.cycleCounter.get())
            assertTrue(coordinator.immediateRetryRequested.get())
            job?.cancelAndJoin()
        }
    }
}
