// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.kwik.core.QuicStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

object AudioStreamPlayer {
    private val playerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioTrack: AudioTrack? = null
    private var activeStream: QuicStream? = null
    private var playJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private data class Chunk(val seq: Int, val pcm: ByteArray)

    @Volatile
    private var tapActive = false

    @Volatile
    private var running = false

    private val audioQueue = ArrayBlockingQueue<Chunk>(200)
    private var playbackThread: Thread? = null

    private val totalDatagramsReceived = AtomicLong(0L)
    private val totalDatagramBytesReceived = AtomicLong(0L)

    private fun createAudioTrack(): AudioTrack {
        val sampleRate = 48000
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val bufferSize = maxOf(minBufferSize * 2, 16384)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track.setVolume(1.0f)
        track.play()
        ConnectionRepository.appendLog("[AUDIO] AudioTrack created and playing (bufferSize=$bufferSize, state=${track.state})")
        return track
    }

    /**
     * Entrega de datagrama cru recebido do Kwik (na thread dedicada do QuicConnectionImpl).
     * O processamento aqui é estritamente não-bloqueante:
     * - Parse de cabeçalho: 2 bytes big-endian seq (u16 com wrap) + resto PCM.
     * - Se tap inativo: descarta imediatamente (evita zumbis).
     * - Enfileira na ArrayBlockingQueue (capacidade 200). Se cheia, descarta o mais velho (drop-oldest).
     */
    fun feedDatagram(data: ByteArray) {
        if (!tapActive) return
        if (data.size <= 2) return

        val seq = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val pcm = data.copyOfRange(2, data.size)
        val chunk = Chunk(seq, pcm)

        totalDatagramsReceived.incrementAndGet()
        totalDatagramBytesReceived.addAndGet(pcm.size.toLong())

        val q = audioQueue
        if (!q.offer(chunk)) {
            q.poll() // descarta o mais velho
            q.offer(chunk)
        }
    }

    /**
     * Inicia a reprodução do audio tap via QUIC DATAGRAM com jitter buffer.
     */
    fun startTap() {
        ConnectionRepository.appendLog("[AUDIO] startTap() ativado")
        stop()

        synchronized(this) {
            audioQueue.clear()
            totalDatagramsReceived.set(0L)
            totalDatagramBytesReceived.set(0L)
            tapActive = true
            running = true
            _isPlaying.value = true

            val thread = Thread({
                runPlaybackLoop()
            }, "AudioTapPlaybackThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            playbackThread = thread
        }
    }

    /**
     * Loop da thread de reprodução dedicada para o audio tap.
     */
    private fun runPlaybackLoop() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (e: Exception) {}

        var track: AudioTrack? = null
        var played = 0L
        var lost = 0L
        var late = 0L
        var stale = 0L
        var totalBytesPlayed = 0L
        var intervalBytes = 0
        var intervalPeak = 0
        var pendingLowByte = -1
        var firstChunkLogged = false
        var expectedSeq = -1
        var preRollDone = false
        var preRollWaitStart = 0L
        var lastStatsTime = System.currentTimeMillis()

        try {
            while (running) {
                val now = System.currentTimeMillis()
                if (now - lastStatsTime >= 10_000) {
                    lastStatsTime = now
                    val queueDepth = audioQueue.size
                    val totalRx = totalDatagramsReceived.get()
                    val avgChunkBytes = if (totalRx > 0) {
                        totalDatagramBytesReceived.get().toFloat() / totalRx
                    } else 960f
                    val mMs = (queueDepth * avgChunkBytes / 192000f * 1000f).toInt()
                    val totalKB = totalBytesPlayed / 1024
                    ConnectionRepository.appendLog(
                        "[AUDIO-STATS] played=$played lost=$lost late=$late stale=$stale dup=0 queueDepth=$queueDepth (~${mMs}ms) total=${totalKB}KB"
                    )
                }

                // Pre-roll: no arranque, espera acumular 8 chunks (~50 ms) antes da primeira escrita
                if (!preRollDone) {
                    val qSize = audioQueue.size
                    if (qSize < 8) {
                        if (qSize > 0) {
                            if (preRollWaitStart == 0L) preRollWaitStart = System.currentTimeMillis()
                            // Se demorar mais de 300ms com chunks na fila, inicia mesmo com menos de 8
                            if (System.currentTimeMillis() - preRollWaitStart > 300) {
                                preRollDone = true
                            } else {
                                try { Thread.sleep(5) } catch (e: InterruptedException) { break }
                                continue
                            }
                        } else {
                            try { Thread.sleep(10) } catch (e: InterruptedException) { break }
                            continue
                        }
                    } else {
                        preRollDone = true
                    }
                }

                val chunk = try {
                    audioQueue.poll(100, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    break
                } ?: continue

                val seq = chunk.seq
                val pcm = chunk.pcm
                var shouldWrite = false

                // Controlo por seq (classificação ao consumir com aritmética modular u16)
                if (expectedSeq == -1) {
                    expectedSeq = (seq + 1) and 0xFFFF
                    shouldWrite = true
                } else {
                    val delta = (seq - expectedSeq) and 0xFFFF
                    if (delta == 0) {
                        // Sequência normal em ordem
                        expectedSeq = (expectedSeq + 1) and 0xFFFF
                        shouldWrite = true
                    } else if (delta < 0x8000) {
                        // seq à frente de expected (gap): conta perda de delta chunks, escreve o atual
                        lost += delta
                        expectedSeq = (seq + 1) and 0xFFFF
                        shouldWrite = true
                    } else {
                        // seq atrás de expected (atrasado / duplicado): descarta, conta late
                        late++
                        shouldWrite = false
                    }
                }

                // Contrapeso anti-backlog: se queue.size > 32 (~200 ms), drena até deixar 8 (~50 ms)
                if (audioQueue.size > 32) {
                    while (audioQueue.size > 8) {
                        val dropped = audioQueue.poll() ?: break
                        stale++
                        expectedSeq = (dropped.seq + 1) and 0xFFFF
                    }
                }

                if (!shouldWrite) continue

                if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
                    track = createAudioTrack()
                    synchronized(this@AudioStreamPlayer) {
                        audioTrack = track
                    }
                }

                played++
                val n = pcm.size
                if (!firstChunkLogged) {
                    firstChunkLogged = true
                    ConnectionRepository.appendLog("[AUDIO] Primeiro chunk via DATAGRAM: $n bytes reproduzido")
                }
                totalBytesPlayed += n
                intervalBytes += n

                // Computa amplitude de pico para diagnóstico de volume nos logs
                var i = 0
                if (pendingLowByte != -1) {
                    val sample = ((pcm[0].toInt() shl 8) or (pendingLowByte and 0xFF)).toShort()
                    val absVal = kotlin.math.abs(sample.toInt())
                    if (absVal > intervalPeak) {
                        intervalPeak = absVal
                    }
                    pendingLowByte = -1
                    i = 1
                }

                while (i + 1 < n) {
                    val sample = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort()
                    val absVal = kotlin.math.abs(sample.toInt())
                    if (absVal > intervalPeak) {
                        intervalPeak = absVal
                    }
                    i += 2
                }

                if (i < n) {
                    pendingLowByte = pcm[i].toInt() and 0xFF
                }

                // Estatísticas de volume periódicas (~1s de áudio = 192000 bytes)
                if (intervalBytes >= 192000) {
                    val pct = (intervalPeak * 100) / 32767
                    ConnectionRepository.appendLog("[AUDIO] Datagram chunk peak amplitude: $intervalPeak / 32767 ($pct%) | Total: ${totalBytesPlayed / 1024} KB")
                    intervalBytes = 0
                    intervalPeak = 0
                }

                // Escrita no AudioTrack em loop até escrever tudo
                var written = 0
                while (written < n && running) {
                    val result = track.write(pcm, written, n - written)
                    if (result < 0) {
                        ConnectionRepository.appendLog("[AUDIO] Error writing datagram chunk to AudioTrack: $result")
                        break
                    }
                    written += result
                }
            }
        } catch (e: Exception) {
            ConnectionRepository.appendLog("[AUDIO] Playback loop error: ${e.message}")
        } finally {
            ConnectionRepository.appendLog("[AUDIO] Encerrando thread de playback do tap...")
            synchronized(this@AudioStreamPlayer) {
                if (audioTrack == track) {
                    audioTrack = null
                }
            }
            try {
                track?.stop()
            } catch (e: Exception) {}
            try {
                track?.release()
            } catch (e: Exception) {}
        }
    }

    /**
     * Compatibilidade retroativa para feedPcmChunk legado.
     */
    @Deprecated("Substituído por feedDatagram com jitter buffer")
    fun feedPcmChunk(pcm: ByteArray) {
        if (!tapActive) return
        val fakeDatagram = ByteArray(2 + pcm.size)
        System.arraycopy(pcm, 0, fakeDatagram, 2, pcm.size)
        feedDatagram(fakeDatagram)
    }

    /**
     * Reprodução legada via stream unidirecional QUIC (mantida para compatibilidade retroativa).
     */
    @Synchronized
    fun playStream(stream: QuicStream) {
        val previousJob = playJob
        stop()

        activeStream = stream
        _isPlaying.value = true
        playJob = playerScope.launch(Dispatchers.IO) {
            try {
                previousJob?.join()
            } catch (e: Exception) {}

            val inp = stream.inputStream
            var track: AudioTrack? = null
            try {
                val sampleRate = 48000
                val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                
                val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBufferSize * 2, 16384)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                synchronized(this@AudioStreamPlayer) {
                    audioTrack = track
                }

                track.setVolume(1.0f)
                track.play()
                ConnectionRepository.appendLog("[AUDIO] AudioTrack started playing (bufferSize=$bufferSize, state=${track.state})")

                val buffer = ByteArray(bufferSize)
                var totalBytesRead = 0L
                var intervalBytes = 0
                var intervalPeak = 0
                var pendingLowByte = -1
                var firstReadLogged = false
                val loopStartMs = System.currentTimeMillis()

                while (isActive) {
                    val n = inp.read(buffer)
                    if (n == -1) {
                        ConnectionRepository.appendLog("[AUDIO] PCM stream reached EOF (total read: $totalBytesRead bytes)")
                        break
                    }
                    if (n > 0) {
                        if (!firstReadLogged) {
                            firstReadLogged = true
                            ConnectionRepository.appendLog("[AUDIO] Primeira leitura: $n bytes em ${System.currentTimeMillis() - loopStartMs}ms")
                        }
                        totalBytesRead += n
                        intervalBytes += n

                        var i = 0
                        if (pendingLowByte != -1) {
                            val sample = ((buffer[0].toInt() shl 8) or (pendingLowByte and 0xFF)).toShort()
                            val absVal = kotlin.math.abs(sample.toInt())
                            if (absVal > intervalPeak) {
                                intervalPeak = absVal
                            }
                            pendingLowByte = -1
                            i = 1
                        }

                        while (i + 1 < n) {
                            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                            val absVal = kotlin.math.abs(sample.toInt())
                            if (absVal > intervalPeak) {
                                intervalPeak = absVal
                            }
                            i += 2
                        }

                        if (i < n) {
                            pendingLowByte = buffer[i].toInt() and 0xFF
                        }

                        if (intervalBytes >= 192000) {
                            val pct = (intervalPeak * 100) / 32767
                            ConnectionRepository.appendLog("[AUDIO] chunk peak amplitude: $intervalPeak / 32767 ($pct%) | Total: ${totalBytesRead / 1024} KB")
                            intervalBytes = 0
                            intervalPeak = 0
                        }

                        var written = 0
                        while (written < n && isActive) {
                            val writeStartMs = System.currentTimeMillis()
                            val result = track.write(buffer, written, n - written)
                            val writeMs = System.currentTimeMillis() - writeStartMs
                            if (writeMs > 200) {
                                ConnectionRepository.appendLog("[AUDIO] track.write demorou ${writeMs}ms (pediu ${n - written} bytes, devolveu $result)")
                            }
                            if (result < 0) {
                                ConnectionRepository.appendLog("[AUDIO] Error writing to AudioTrack: $result")
                                break
                            }
                            written += result
                        }
                    }
                }
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[AUDIO] Playback error: ${e.message}")
            } finally {
                ConnectionRepository.appendLog("[AUDIO] Cleaning up AudioTrack...")
                var needRelease = false
                synchronized(this@AudioStreamPlayer) {
                    if (audioTrack == track) {
                        audioTrack = null
                        needRelease = true
                    }
                    if (activeStream == stream) {
                        activeStream = null
                    }
                }
                if (needRelease) {
                    try {
                        track?.stop()
                    } catch (e: Exception) {}
                    try {
                        track?.release()
                    } catch (e: Exception) {}
                }
                try {
                    stream.abortReading(0)
                } catch (e: Exception) {}
                _isPlaying.value = false
            }
        }
    }

    /**
     * Encerramento do áudio:
     * 1. Marca tapActive = false (descarta imediatamente novos datagramas recebidos).
     * 2. Limpa a fila e interrompe/aguarda o término da thread de playback.
     * 3. Fecha conexões legadas se houver.
     * 4. Libera o AudioTrack com segurança.
     */
    fun stop() {
        val caller = Thread.currentThread().stackTrace.getOrNull(3)?.let {
            "${it.className}.${it.methodName}:${it.lineNumber}"
        } ?: "desconhecido"
        ConnectionRepository.appendLog("[AUDIO] stop() chamado de: $caller")

        val thread: Thread?
        val streamToClose: QuicStream?
        val jobToCancel: Job?

        synchronized(this) {
            tapActive = false
            audioQueue.clear()
            running = false

            thread = playbackThread
            playbackThread = null

            streamToClose = activeStream
            activeStream = null

            jobToCancel = playJob
            playJob = null

            _isPlaying.value = false
        }

        // 1. Termina a thread de playback (flag + interrupt()/join curto com timeout)
        if (thread != null) {
            try {
                thread.interrupt()
                thread.join(300)
            } catch (e: Exception) {}
        }

        // 2. Limpa conexões de stream unidirecional se houver
        try {
            streamToClose?.abortReading(0)
        } catch (e: Exception) {}
        jobToCancel?.cancel()

        // 3. E só depois liberta o AudioTrack
        val trackToRelease: AudioTrack?
        synchronized(this) {
            trackToRelease = audioTrack
            audioTrack = null
        }
        if (trackToRelease != null) {
            try {
                trackToRelease.stop()
            } catch (e: Exception) {}
            try {
                trackToRelease.release()
            } catch (e: Exception) {}
        }
    }
}
