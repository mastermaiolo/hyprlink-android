// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Coordenador single-flight do ciclo de ligação ao PC.
 * Garante que nunca correm dois ciclos em paralelo, cancela e aguarda o término
 * do ciclo anterior com timeout curto e fecha explicitamente conexões prévias.
 */
class ConnectionLoopCoordinator(
    private val scope: CoroutineScope,
    private val joinTimeoutMs: Long = 3000L
) {
    val cycleCounter = AtomicInteger(0)
    val immediateRetryRequested = AtomicBoolean(false)
    val mutex = Mutex()

    @Volatile
    var currentJob: Job? = null
        private set

    @Volatile
    var isAttemptInProgress: Boolean = false

    @Volatile
    var lastStationId: String? = null

    /**
     * Pede o arranque do ciclo de ligação de forma single-flight.
     * Retorna o Job ativo ou recém-criado.
     */
    suspend fun requestLoop(
        stationId: String,
        isConnected: Boolean,
        onClosePreviousConnection: (() -> Unit)? = null,
        loopRunner: suspend (cycleId: Int) -> Unit
    ): Job? = mutex.withLock {
        // Se já está a correr um ciclo para a mesma estação com job ativo, reaproveita
        if (stationId == lastStationId && currentJob?.isActive == true) {
            if (!isConnected) {
                // Marca retentativa imediata para o job existente não ficar preso em backoff longo
                immediateRetryRequested.set(true)
            }
            return currentJob
        }

        lastStationId = stationId

        // Fecha explicitamente a ligação anterior para desbloquear I/O bloqueante (handshake)
        onClosePreviousConnection?.invoke()

        // Cancela o job anterior com timeout curto
        val old = currentJob
        if (old != null && old.isActive) {
            withTimeoutOrNull(joinTimeoutMs) {
                old.cancelAndJoin()
            }
        }

        val cycleId = cycleCounter.incrementAndGet()
        val newJob = scope.launch {
            loopRunner(cycleId)
        }
        currentJob = newJob
        return newJob
    }
}
