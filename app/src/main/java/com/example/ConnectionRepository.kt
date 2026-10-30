// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Bitmap
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.core.app.NotificationCompat
import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.HalfPrecisionFloat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import tech.kwik.core.QuicClientConnection
import tech.kwik.core.QuicStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.content.FileProvider
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

data class Packet(
    val id: Long,
    val type: String,
    val body: Map<String, Any?>?,
    val hasPayload: Boolean = false
)

data class MediaState(
    val player: String,
    val status: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val positionMs: Long? = null,
    val lengthMs: Long? = null,
    val artBytes: ByteArray? = null,
    val artId: String? = null
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class TransferDirection { UPLOAD, DOWNLOAD }

enum class TransferStatus {
    EM_CURSO,
    VERIFICADO,
    NAO_VERIFICADO,
    ERRO
}

data class TransferItem(
    val id: Long,
    val name: String,
    val size: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val progress: Float, // 0f to 1f
    val bytesTransferred: Long,
    val error: String? = null,
    val sha256Local: String? = null,
    val sha256Remote: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val uri: android.net.Uri? = null,
    val savedNameOnPc: String? = null,
    val bytesPerSec: Long? = null,
    val etaSeconds: Long? = null,
    val durationMs: Long? = null
)

data class ShareDonePacket(
    val id: Long,
    val ok: Boolean,
    val sha256: String,
    val bytes: Long,
    val error: String?,
    val name: String? = null
)

data class DownloadSession(
    val uri: android.net.Uri,
    var localSha256: String? = null,
    var isStreamFinished: Boolean = false,
    val doneDeferred: CompletableDeferred<ShareDonePacket> = CompletableDeferred()
)

data class PcActionResult(
    val ok: Boolean,
    val error: String? = null
)

data class WebcamRequestResult(
    val ok: Boolean,
    val error: String? = null
)

data class AudioSink(
    val id: Long,
    val name: String,
    val description: String,
    val volume: Int,
    val muted: Boolean,
    val is_default: Boolean,
    val is_phone: Boolean
)

data class AudioApp(
    val id: Long,
    val name: String,
    val media: String?,
    val volume: Int,
    val muted: Boolean,
    val sink_id: Long
)

data class AudioState(
    val default_sink: String,
    val sinks: List<AudioSink>,
    val apps: List<AudioApp>
)

data class PcStatus(
    val hostname: String? = null,
    val cpuPct: Float? = null,
    val ramUsedBytes: Long? = null,
    val ramTotalBytes: Long? = null,
    val cpuTempC: Float? = null,
    val uptimeSeconds: Long? = null,
    val rttMs: Float? = null,
    val diskFreeBytes: Long? = null,
    val diskTotalBytes: Long? = null,
    val gpuPct: Float? = null
)

object ConnectionRepository {
    private val repositoryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val packetIdCounter = AtomicLong(1)
    
    var lastReceivedClipboardText: String = ""
    var lastSentClipboardText: String = ""

    private val fileSendMutex = kotlinx.coroutines.sync.Mutex()

    @Volatile
    private var appContext: Context? = null

    private val logLock = Any()

    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(context: Context) {
        appContext = context.applicationContext
        logScope.launch {
            synchronized(logLock) {
                try {
                    val logFile = java.io.File(context.filesDir, "diagnostics.log")
                    if (logFile.exists()) {
                        val lines = logFile.readLines()
                        val lastLines = if (lines.size > 1000) lines.takeLast(1000) else lines
                        _logs.value = lastLines
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HyprLink", "Failed to load initial logs from file", e)
                }
            }
        }
    }

    data class FileAnnouncement(val name: String, val size: Long)
    private val fileAnnouncements = java.util.concurrent.ConcurrentHashMap<Long, FileAnnouncement>()

    // Active downloads map
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<Long, DownloadSession>()

    // Transfer Rate & ETA monitoring (Prompt 61)
    private val transferMeters = java.util.concurrent.ConcurrentHashMap<Long, TransferRateMeter>()
    private val transferStartTimes = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private val transferLastNotifTimes = java.util.concurrent.ConcurrentHashMap<Long, Long>()
    private var transferMonitorJob: Job? = null

    private const val CHANNEL_TRANSFER_PROGRESS = "transfer_progress_channel"
    private const val NOTIFICATION_TRANSFER_BASE_ID = 40000

    fun sampleTransfer(id: Long, bytes: Long, total: Long) {
        val startTime = transferStartTimes.computeIfAbsent(id) {
            android.os.SystemClock.elapsedRealtime()
        }
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startTime).coerceAtLeast(0L)
        val meter = transferMeters.computeIfAbsent(id) { TransferRateMeter() }
        meter.sample(elapsed, bytes)
    }

    fun getTransferRateAndEta(id: Long, bytes: Long, total: Long): Pair<Long?, Long?> {
        val startTime = transferStartTimes[id] ?: return Pair(null, null)
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startTime).coerceAtLeast(0L)
        val meter = transferMeters[id] ?: return Pair(null, null)
        return Pair(meter.bytesPerSec(), meter.etaSeconds(elapsed, bytes, total))
    }

    fun startTransferMonitorIfNeeded() {
        if (transferMonitorJob?.isActive == true) return
        transferMonitorJob = repositoryScope.launch {
            while (isActive) {
                delay(1000L)
                val active = _transfers.value.filter { it.status == TransferStatus.EM_CURSO }
                if (active.isEmpty()) {
                    break
                }
                val context = appContext
                for (item in active) {
                    sampleTransfer(item.id, item.bytesTransferred, item.size)
                    val (bps, eta) = getTransferRateAndEta(item.id, item.bytesTransferred, item.size)
                    if (bps != item.bytesPerSec || eta != item.etaSeconds) {
                        val updated = item.copy(bytesPerSec = bps, etaSeconds = eta)
                        updateTransfer(updated)
                        if (context != null) {
                            updateTransferNotification(context, updated)
                        }
                    }
                }
            }
        }
    }

    fun updateTransferNotification(
        context: Context,
        transfer: TransferItem,
        force: Boolean = false
    ) {
        val id = transfer.id
        val now = android.os.SystemClock.elapsedRealtime()
        val lastNotif = transferLastNotifTimes[id] ?: 0L

        // Throttle de no máximo 1x por segundo (a menos que seja forçado no arranque)
        if (!force && (now - lastNotif < 1000L)) {
            return
        }
        transferLastNotifTimes[id] = now

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_TRANSFER_PROGRESS,
            "Transferências",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Progresso de transferências de ficheiros"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val strings = LanguageManager.currentStrings
        val speedText = TransferRateFormatter.transferSpeed(transfer.bytesPerSec, transfer.etaSeconds, strings)
        val notifId = (NOTIFICATION_TRANSFER_BASE_ID + (id % 10000)).toInt()

        val subText = if (transfer.direction == TransferDirection.DOWNLOAD) strings.transferToPhone else strings.transferToPc
        val progressInt = (transfer.progress * 100).toInt().coerceIn(0, 100)

        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSFER_PROGRESS)
            .setContentTitle(transfer.name)
            .setContentText(speedText)
            .setSubText(subText)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setProgress(100, progressInt, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(notifId, notification)
    }

    fun cancelTransferNotification(context: Context, id: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = (NOTIFICATION_TRANSFER_BASE_ID + (id % 10000)).toInt()
        notificationManager.cancel(notifId)
        transferLastNotifTimes.remove(id)
    }

    fun notifyTransferSuccess(context: Context, transfer: TransferItem) {
        cancelTransferNotification(context, transfer.id)
        val strings = LanguageManager.currentStrings
        val title = if (transfer.direction == TransferDirection.DOWNLOAD) {
            strings.transferReceivedTitle
        } else {
            strings.transferSentTitle
        }
        val summary = if (transfer.durationMs != null && transfer.bytesPerSec != null) {
            TransferRateFormatter.transferSummary(transfer.size, transfer.durationMs, transfer.bytesPerSec, strings)
        } else {
            TransferRateFormatter.formatBytes(transfer.size, strings.decimalComma)
        }
        postNotification(context, title, "${transfer.name}: $summary", "HyprLink")
    }

    // Observable transfers list
    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers = _transfers.asStateFlow()

    fun updateTransfer(item: TransferItem) {
        val current = _transfers.value.toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index != -1) {
            current[index] = item
        } else {
            current.add(0, item) // Insert at top (newest first)
        }
        _transfers.value = current

        if (item.status == TransferStatus.EM_CURSO) {
            startTransferMonitorIfNeeded()
        } else {
            appContext?.let { cancelTransferNotification(it, item.id) }
        }

        // Persist completed / terminal transfers to Room
        if (item.status == TransferStatus.VERIFICADO ||
            item.status == TransferStatus.NAO_VERIFICADO ||
            item.status == TransferStatus.ERRO
        ) {
            val context = appContext
            if (context != null) {
                repositoryScope.launch {
                    try {
                        val db = AppDatabase.getDatabase(context)
                        val record = TransferRecord(
                            name = item.name,
                            size = item.size,
                            direction = item.direction.name,
                            status = item.status.name,
                            sha256Local = item.sha256Local,
                            sha256Remote = item.sha256Remote,
                            error = item.error,
                            timestamp = item.timestamp
                        )
                        db.transferRecordDao().insert(record)
                        appendLog("[ROOM] Persisted transfer record for ${item.name} (${item.status})")
                    } catch (e: Exception) {
                        appendLog("[ROOM] Failed to persist transfer: ${e.message}")
                    }
                }
            }
        }
    }

    fun getPersistentHistory(context: Context): Flow<List<TransferRecord>> {
        val db = AppDatabase.getDatabase(context)
        return db.transferRecordDao().getRecent()
    }

    fun clearPersistentHistory(context: Context) {
        val contextToUse = appContext ?: context.applicationContext
        repositoryScope.launch {
            try {
                val db = AppDatabase.getDatabase(contextToUse)
                db.transferRecordDao().clearAll()
                appendLog("[ROOM] Cleared persistent transfer history")
            } catch (e: Exception) {
                appendLog("[ROOM] Failed to clear persistent history: ${e.message}")
            }
        }
    }

    fun clearTransfers() {
        _transfers.value = emptyList()
    }

    // Observable states
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _batteryLevel = MutableStateFlow<Int?>(null)
    val batteryLevel = _batteryLevel.asStateFlow()

    private val _batteryCharging = MutableStateFlow<Boolean?>(null)
    val batteryCharging = _batteryCharging.asStateFlow()

    private val _pcBattery = MutableStateFlow<PcBattery?>(null)
    val pcBattery = _pcBattery.asStateFlow()

    private val _pcStatus = MutableStateFlow<PcStatus?>(null)
    val pcStatus = _pcStatus.asStateFlow()

    private val _pcShortcuts = MutableStateFlow<List<PcShortcut>>(emptyList())
    val pcShortcuts = _pcShortcuts.asStateFlow()

    private val _mediaState = MutableStateFlow<MediaState?>(null)
    val mediaState = _mediaState.asStateFlow()

    val currentArtworkBitmap = MutableStateFlow<Bitmap?>(null)

    private val _fileSendingProgress = MutableStateFlow<Float?>(null)
    val fileSendingProgress = _fileSendingProgress.asStateFlow()

    private val _fileSendingStatus = MutableStateFlow<String?>(null)
    val fileSendingStatus = _fileSendingStatus.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _hyprEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val hyprEvents = _hyprEvents.asSharedFlow()

    private val _mirroredNotificationsCount = MutableStateFlow(0)
    val mirroredNotificationsCount = _mirroredNotificationsCount.asStateFlow()

    private val stationPcAddrs = java.util.concurrent.ConcurrentHashMap<String, List<PcAddr>>()

    private val _currentLinkPath = MutableStateFlow(LinkPathType.WIFI)
    val currentLinkPath = _currentLinkPath.asStateFlow()

    @Volatile
    var currentStationId: String? = null

    fun getStationPcAddrs(stationId: String): List<PcAddr> {
        return stationPcAddrs[stationId] ?: emptyList()
    }

    fun updateStationPcAddrs(stationId: String, addrs: List<PcAddr>) {
        stationPcAddrs[stationId] = addrs
    }

    fun setLinkPath(path: LinkPathType, targetIp: String? = null) {
        val old = _currentLinkPath.value
        _currentLinkPath.value = path
        if (old != path) {
            val ipSuffix = if (!targetIp.isNullOrBlank()) " ($targetIp)" else ""
            val logMsg = when (path) {
                LinkPathType.USB -> "[LIGAÇÃO] Ligado por cabo USB$ipSuffix"
                LinkPathType.WIFI -> "[LIGAÇÃO] Ligado por Wi-Fi$ipSuffix"
                LinkPathType.ETH -> "[LIGAÇÃO] Ligado por cabo de rede Ethernet$ipSuffix"
                LinkPathType.OTHER -> "[LIGAÇÃO] Ligado por rede alternativa$ipSuffix"
            }
            appendLog(logMsg)
        }
    }

    fun incrementMirroredCount() {
        _mirroredNotificationsCount.value++
    }

    var activeConnection: QuicClientConnection? = null
        private set

    fun checkStalledTransfers() {
        val now = System.currentTimeMillis()
        val current = _transfers.value
        current.forEach { item ->
            if (item.status == TransferStatus.EM_CURSO) {
                if (item.progress >= 1f || (item.size > 0 && item.bytesTransferred >= item.size)) {
                    updateTransfer(item.copy(status = TransferStatus.VERIFICADO, progress = 1f, bytesTransferred = item.size))
                } else if (item.bytesTransferred == 0L && (now - item.timestamp > 30_000L)) {
                    updateTransfer(item.copy(
                        status = TransferStatus.ERRO,
                        error = "Tempo limite excedido (sem progresso após 30s)"
                    ))
                }
            }
        }
    }

    val diagnosticsBuffer = DiagnosticsLogBuffer(
        logFileProvider = {
            appContext?.let { java.io.File(it.filesDir, "diagnostics.log") }
        },
        coroutineScope = logScope
    )

    fun appendLog(record: LogRecord) {
        val formattedMsg = record.toNeutralString()
        val enMsg = if (record.timestamp.isNotEmpty()) "[${record.timestamp}] ${TerminalLogTranslator.formatEn(record)}" else TerminalLogTranslator.formatEn(record)

        try {
            android.util.Log.d("HyprLink", enMsg)
        } catch (e: Throwable) {
            println(enMsg)
        }

        val isErrorOrWarn = record.tag.contains("ERR", ignoreCase = true) ||
                            record.tag.contains("WARN", ignoreCase = true) ||
                            record.key.contains("err", ignoreCase = true) ||
                            enMsg.contains("[ERR]", ignoreCase = true) ||
                            enMsg.contains("[WARN]", ignoreCase = true)

        diagnosticsBuffer.append(formattedMsg, enMsg, isErrorOrWarn)
        _logs.value = diagnosticsBuffer.getMemoryLogs()
    }

    fun appendLog(tag: String, key: String, vararg args: String) {
        val timePrefix = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        appendLog(LogRecord(timestamp = timePrefix, tag = tag, key = key, args = args.toList()))
    }

    fun appendLog(message: String) {
        val timePrefix = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val record = LogRecord.fromRaw(timePrefix, message)
        appendLog(record)
    }

    fun getFullLogText(): String {
        return try {
            val lang = LanguageManager.currentLanguage.value
            diagnosticsBuffer.getMemoryLogs().joinToString("\n") { line ->
                TerminalLogTranslator.translate(line, lang, isTechnical = true)
            }
        } catch (e: Exception) {
            diagnosticsBuffer.getMemoryLogs().joinToString("\n")
        }
    }

    fun clearLogs() {
        diagnosticsBuffer.clear()
        _logs.value = emptyList()
    }

    fun setStatus(status: ConnectionStatus) {
        val old = _connectionStatus.value
        _connectionStatus.value = status
        if (status == ConnectionStatus.DISCONNECTED) {
            _batteryLevel.value = null
            _batteryCharging.value = null
            _pcBattery.value = null
            _pcShortcuts.value = emptyList()
            _mediaState.value = null
            currentArtworkBitmap.value = null
            _mirroredNotificationsCount.value = 0
            if (old == ConnectionStatus.CONNECTED) {
                appendLog("Ligação perdida")
            }
        }
    }

    fun registerActiveConnection(
        connection: QuicClientConnection,
        context: Context,
        pathType: LinkPathType = LinkPathType.WIFI,
        targetIp: String? = null
    ) {
        appContext = context.applicationContext
        val oldConn = activeConnection
        if (oldConn != null && oldConn != connection) {
            appendLog("[CONN] Substituída ligação anterior por nova ligação ativa")
            try {
                oldConn.close()
            } catch (_: Exception) {}
        }
        activeConnection = connection
        setLinkPath(pathType, targetIp)
        setStatus(ConnectionStatus.CONNECTED)
        appendLog("[CONN] Registered active connection!")
        appendLog("PC ligado")

        if (WebcamStreamer.isStreaming.value) {
            WebcamStreamer.onConnectionUpdated(connection)
        }

        // Listen for peer-initiated streams (from daemon)
        connection.setPeerInitiatedStreamCallback(java.util.function.Consumer { stream ->
            repositoryScope.launch {
                if (stream.isUnidirectional()) {
                    handlePeerUniStream(stream, context)
                } else {
                    handlePeerStream(stream, context)
                }
            }
        })

        // Handler de QUIC DATAGRAM (novo caminho do audio tap do daemon com jitter buffer)
        // Formato: 2 bytes u16 big-endian (sequence counter) + PCM cru (16-bit LE, 48kHz estéreo)
        connection.setDatagramHandler { data: ByteArray ->
            AudioStreamPlayer.feedDatagram(data)
        }

        // Request initial battery status immediately on connection
        repositoryScope.launch {
            try {
                sendBatteryRequest()
            } catch (e: Exception) {
                appendLog("[WARN] Initial battery request failed: ${e.message}")
            }
        }
    }

    fun clearActiveConnection() {
        activeConnection = null
        _currentLinkPath.value = LinkPathType.WIFI
        _pcStatus.value = null
        _pcShortcuts.value = emptyList()
        AudioStreamPlayer.stop()
        setStatus(ConnectionStatus.DISCONNECTED)
    }

    // --- PACKET SENDING METHODS ---

    suspend fun sendClipboardText(text: String) = withContext(Dispatchers.IO) {
        lastSentClipboardText = text
        appendLog("[CLIPBOARD] Sending clipboard text to workstation...")
        val body = mapOf("text" to text)
        sendOneWayPacket("clipboard.set", body)
    }

    suspend fun sendClipboardImageFromUri(context: Context, uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: run {
                appendLog("[ERROR] Failed to read image from Uri: $uri")
                return@withContext false
            }
        val pngBytes = convertOrEnsurePng(rawBytes)
        return@withContext sendClipboardImage(pngBytes)
    }

    private fun convertOrEnsurePng(rawBytes: ByteArray): ByteArray {
        val isPng = rawBytes.size >= 8 &&
            rawBytes[0] == 0x89.toByte() &&
            rawBytes[1] == 0x50.toByte() &&
            rawBytes[2] == 0x4E.toByte() &&
            rawBytes[3] == 0x47.toByte() &&
            rawBytes[4] == 0x0D.toByte() &&
            rawBytes[5] == 0x0A.toByte() &&
            rawBytes[6] == 0x1A.toByte() &&
            rawBytes[7] == 0x0A.toByte()

        if (isPng) return rawBytes

        return try {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (bitmap != null) {
                val baos = ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            } else {
                rawBytes
            }
        } catch (e: Exception) {
            rawBytes
        }
    }

    suspend fun sendClipboardImage(pngBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection
        if (conn == null) {
            appendLog("[ERROR] Cannot send clipboard image: Not connected to workstation")
            return@withContext false
        }
        val packetId = packetIdCounter.incrementAndGet()
        val size = pngBytes.size.toLong()
        try {
            appendLog("[CLIP] Sending clipboard image: $size bytes, packetId=$packetId")

            // 1. Send clipboard.set Packet on a control stream
            val packet = Packet(
                id = packetId,
                type = "clipboard.set",
                body = mapOf("mime" to "image/png", "size" to size),
                hasPayload = true
            )
            val controlStream = conn.createStream(true)
            val cborBytes = encodePacket(packet)
            val outCtrl = controlStream.outputStream
            writeFramedBytes(outCtrl, cborBytes)
            outCtrl.close() // half-close

            // Wait for EOF on control stream
            val inCtrl = controlStream.inputStream
            try {
                ConnectionUtils.readWithDeadline(controlStream, 10_000) {
                    inCtrl.readBytes()
                }
            } catch (e: Exception) {}
            try { controlStream.abortReading(0) } catch (e: Exception) {}

            // 2. Open unidirectional stream for PNG payload
            val uniStream = conn.createStream(false)
            val outUni = uniStream.outputStream

            // Write 8-byte big-endian packet ID
            val idBytes = ByteArray(8)
            for (i in 0..7) {
                idBytes[i] = ((packetId shr (56 - i * 8)) and 0xFF).toByte()
            }
            outUni.write(idBytes)

            // Write PNG bytes and close with EOF
            outUni.write(pngBytes)
            outUni.flush()
            outUni.close()

            appendLog("[CLIP] Imagem PNG enviada para o PC: ${size / 1024} KB")
            return@withContext true
        } catch (e: Exception) {
            appendLog("[ERROR] Failed to send clipboard image: ${e.message}")
            return@withContext false
        }
    }

    suspend fun sendPhoneBatteryState(level: Int, charging: Boolean) = withContext(Dispatchers.IO) {
        appendLog("[BATTERY] Sending phone battery state: level=$level, charging=$charging")
        val body = mapOf("level" to level, "charging" to charging)
        sendOneWayPacket("battery.state", body)
    }

    suspend fun sendPhoneStatus(status: Map<String, Any?>) = withContext(Dispatchers.IO) {
        appendLog("[PHONE] Sending phone.status (${status.size} fields)")
        sendOneWayPacket("phone.status", status)
    }

    suspend fun sendMediaCommand(command: String) = withContext(Dispatchers.IO) {
        appendLog("[MEDIA] Sending media command: $command")
        val body = mapOf("command" to command)
        sendOneWayPacket("media.command", body)
    }

    suspend fun sendUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        if (!ShareUrlValidator.isValidUrl(url)) {
            appendLog("[SHARE] URL inválido recusado (apenas http/https permitidos): $url")
            return@withContext false
        }
        appendLog("[SHARE] Sending URL: $url")
        val body = mapOf("url" to url)
        sendOneWayPacket("share.url", body)
        true
    }

    suspend fun sendInputMove(dx: Int, dy: Int) = withContext(Dispatchers.IO) {
        val body = mapOf("dx" to dx, "dy" to dy)
        sendOneWayPacket("input.move", body)
    }

    suspend fun sendInputScroll(dx: Int, dy: Int) = withContext(Dispatchers.IO) {
        val body = mapOf("dx" to dx, "dy" to dy)
        sendOneWayPacket("input.scroll", body)
    }

    suspend fun sendInputClick(button: String) = withContext(Dispatchers.IO) {
        val body = mapOf("button" to button)
        sendOneWayPacket("input.click", body)
    }

    suspend fun sendMouseButton(button: String, down: Boolean) = withContext(Dispatchers.IO) {
        val body = mapOf("button" to button, "state" to if (down) "down" else "up")
        sendOneWayPacket("input.button", body)
    }

    suspend fun releaseAllMouseButtons() = withContext(Dispatchers.IO) {
        sendMouseButton("left", false)
        sendMouseButton("middle", false)
        sendMouseButton("right", false)
    }

    suspend fun sendInputType(text: String) = withContext(Dispatchers.IO) {
        val body = mapOf("text" to text)
        sendOneWayPacket("input.type", body)
    }

    suspend fun sendInputKey(key: String) = withContext(Dispatchers.IO) {
        val body = mapOf("key" to key)
        sendOneWayPacket("input.key", body)
    }

    suspend fun sendGesture(name: String, dir: String? = null) = withContext(Dispatchers.IO) {
        if (activeConnection == null) return@withContext
        val body = mutableMapOf<String, Any>("name" to name)
        if (dir != null) {
            body["dir"] = dir
        }
        sendOneWayPacket("gesture", body)
        appendLog("gesture $name${if (dir != null) " $dir" else ""}")
    }

    suspend fun fetchHyprWorkspaces(): String = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "hypr.workspaces", body = null, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            val respBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "hypr.workspaces_state") {
                val ok = respPacket.body?.get("ok") as? Boolean ?: false
                val data = respPacket.body?.get("data") as? String ?: ""
                if (ok) {
                    return@withContext data
                } else {
                    throw Exception(data.ifEmpty { "Workspaces retrieval returned error status" })
                }
            } else {
                throw Exception("Received unexpected packet type in response: ${respPacket?.type}")
            }
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun fetchHyprClients(): String = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "hypr.clients", body = null, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            val respBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "hypr.clients_state") {
                val ok = respPacket.body?.get("ok") as? Boolean ?: false
                val data = respPacket.body?.get("data") as? String ?: ""
                if (ok) {
                    return@withContext data
                } else {
                    throw Exception(data.ifEmpty { "Clients retrieval returned error status" })
                }
            } else {
                throw Exception("Received unexpected packet type in response: ${respPacket?.type}")
            }
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun dispatchHyprCommand(cmd: String): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "hypr.dispatch", body = mapOf("cmd" to cmd), hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            val respBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "hypr.dispatch_result") {
                val ok = respPacket.body?.get("ok") as? Boolean ?: false
                val data = respPacket.body?.get("data") as? String ?: ""
                if (ok) {
                    return@withContext true
                } else {
                    throw Exception(data.ifEmpty { "Dispatch command rejected" })
                }
            } else {
                throw Exception("Received unexpected packet type in response: ${respPacket?.type}")
            }
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun sendPcAction(name: String, timeoutMs: Long? = null): PcActionResult = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "pc.action", body = mapOf("name" to name), hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        val effectiveTimeout = timeoutMs ?: when (name) {
            "screenshot_area" -> 70_000L
            "screenshot" -> 15_000L
            else -> 10_000L
        }

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            val respBytes = ConnectionUtils.readWithDeadline(stream, effectiveTimeout) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "pc.action_result") {
                val ok = respPacket.body?.get("ok") as? Boolean ?: false
                val error = respPacket.body?.get("error") as? String
                PcActionResult(ok = ok, error = error)
            } else if (respPacket != null && respPacket.body?.containsKey("error") == true) {
                PcActionResult(ok = false, error = respPacket.body["error"] as? String ?: "Unknown error")
            } else {
                throw Exception("unknown command")
            }
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun runShortcut(id: String, timeoutMs: Long = 10_000L): ShortcutRunResult = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "shortcut.run", body = mapOf("id" to id), hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            val respBytes = ConnectionUtils.readWithDeadline(stream, timeoutMs) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "shortcut.run_result") {
                val respId = respPacket.body?.get("id") as? String ?: id
                val ok = respPacket.body?.get("ok") as? Boolean ?: false
                val error = respPacket.body?.get("error") as? String
                ShortcutRunResult(id = respId, ok = ok, error = error)
            } else if (respPacket != null && respPacket.body?.containsKey("error") == true) {
                val respId = respPacket.body?.get("id") as? String ?: id
                val error = respPacket.body["error"] as? String ?: "failed"
                ShortcutRunResult(id = respId, ok = false, error = error)
            } else {
                throw Exception("unknown command")
            }
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    fun handleShortcutsList(body: Map<String, Any?>?) {
        val list = PcShortcutLogic.parseShortcutsList(body)
        _pcShortcuts.value = list
        appendLog("[SHORTCUTS] Lista de atalhos atualizada: ${list.size} atalho(s)")
    }

    suspend fun requestWebcam(
        width: Int? = null,
        height: Int? = null,
        fps: Int? = null,
        codec: String? = null,
        timeoutMs: Long = 10_000L
    ): WebcamRequestResult = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        val body = WebcamLogic.buildWebcamRequestBody(width, height, fps, codec)

        val packet = Packet(id = packetId, type = "webcam.request", body = body, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            val respBytes = ConnectionUtils.readWithDeadline(stream, timeoutMs) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "webcam.request_result") {
                val ok = respPacket.body?.get("ok") as? Boolean ?: false
                val error = respPacket.body?.get("error") as? String
                WebcamRequestResult(ok = ok, error = error)
            } else if (respPacket != null && respPacket.body?.containsKey("error") == true) {
                WebcamRequestResult(ok = false, error = respPacket.body["error"] as? String ?: "Unknown error")
            } else {
                WebcamRequestResult(ok = false, error = "unknown response")
            }
        } catch (e: Exception) {
            val isTimeout = e is kotlinx.coroutines.TimeoutCancellationException ||
                (e is java.io.IOException && e.message?.contains("timed out", ignoreCase = true) == true)
            if (isTimeout) {
                WebcamRequestResult(ok = false, error = "timeout")
            } else {
                WebcamRequestResult(ok = false, error = e.message ?: "unknown error")
            }
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun sendBatteryRequest() = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        appendLog("[BATTERY] Requesting workstation battery status (packetId=$packetId)...")

        val packet = Packet(id = packetId, type = "battery.request", body = null, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        try {
            // Write request
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            // Read response on same stream
            val respBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "battery.state") {
                handleBatteryState(respPacket.body)
            } else {
                appendLog("[BATTERY] Received unexpected packet type in response: ${respPacket?.type}")
            }
        } catch (e: Exception) {
            appendLog("[BATTERY] Error requesting battery: ${e.message}")
            throw e
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun sendPing(): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext false
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "core.ping", body = null, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream
        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output
            
            val respBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "core.pong") {
                return@withContext true
            } else {
                appendLog("[PING] Received unexpected packet type in response: ${respPacket?.type}")
                return@withContext false
            }
        } catch (e: Exception) {
            appendLog("[PING] Ping failed: ${e.message}")
            return@withContext false
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun sendOneWayPacket(type: String, body: Map<String, Any?>?) {
        sendAnnouncedPacket(type, body, hasPayload = false)
    }

    suspend fun sendAnnouncedPacket(type: String, body: Map<String, Any?>?, hasPayload: Boolean = true): Long? {
        val conn = activeConnection ?: return null
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = type, body = body, hasPayload = hasPayload)
        val stream = conn.createStream(true)
        val out = stream.outputStream

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close
            
            if (hasPayload) {
                // Read any response/acknowledgement (EOF) to ensure flush
                val inp = stream.inputStream
                ConnectionUtils.readWithDeadline(stream, 10_000) {
                    inp.readBytes()
                }
            }
            return packetId
        } catch (e: Exception) {
            appendLog("[WARN] Failed to send packet $type: ${e.message}")
            return null
        } finally {
            // Fechar SEMPRE ambos os lados — um send falhado deixava o output
            // aberto e esgotava os stream credits QUIC (matava sends seguintes)
            try { out.close() } catch (e: Exception) {}
            try { stream.abortReading(0) } catch (e: Exception) {}
        }
    }

    suspend fun fetchAudioState(): AudioState = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: throw IllegalStateException("Not connected")
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "audio.state", body = null, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream

        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close() // Half-close output

            val respBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "audio.state_reply") {
                val body = respPacket.body ?: throw Exception("Empty body in audio.state_reply")
                val defaultSink = body["default_sink"] as? String ?: ""
                
                val sinksList = (body["sinks"] as? List<*>)?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val id = (map["id"] as? Number)?.toLong() ?: 0L
                    val name = map["name"] as? String ?: ""
                    val description = map["description"] as? String ?: ""
                    val volume = (map["volume"] as? Number)?.toInt() ?: 0
                    val muted = map["muted"] as? Boolean ?: false
                    val is_default = map["is_default"] as? Boolean ?: false
                    val is_phone = map["is_phone"] as? Boolean ?: false
                    AudioSink(id, name, description, volume, muted, is_default, is_phone)
                } ?: emptyList()

                val appsList = (body["apps"] as? List<*>)?.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    val id = (map["id"] as? Number)?.toLong() ?: 0L
                    val name = map["name"] as? String ?: ""
                    val media = map["media"] as? String
                    val volume = (map["volume"] as? Number)?.toInt() ?: 0
                    val muted = map["muted"] as? Boolean ?: false
                    val sink_id = (map["sink_id"] as? Number)?.toLong() ?: 0L
                    AudioApp(id, name, media, volume, muted, sink_id)
                } ?: emptyList()

                return@withContext AudioState(defaultSink, sinksList, appsList)
            } else {
                throw Exception("Received unexpected packet type in response: ${respPacket?.type}")
            }
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
        }
    }

    suspend fun setAudioVolume(kind: String, id: Long, volume: Int): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext false
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(
            id = packetId,
            type = "audio.set_volume",
            body = mapOf("kind" to kind, "id" to id, "volume" to volume),
            hasPayload = false
        )
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream
        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close()
            val respBytes = ConnectionUtils.readWithDeadline(stream, 5000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            return@withContext (respPacket != null && respPacket.type == "audio.ack")
        } catch (e: Exception) {
            appendLog("[WARN] Failed to set audio volume: ${e.message}")
            return@withContext false
        } finally {
            try { stream.abortReading(0) } catch (e: Exception) {}
        }
    }

    suspend fun setAudioMute(kind: String, id: Long, muted: Boolean): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext false
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(
            id = packetId,
            type = "audio.set_mute",
            body = mapOf("kind" to kind, "id" to id, "muted" to muted),
            hasPayload = false
        )
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream
        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close()
            val respBytes = ConnectionUtils.readWithDeadline(stream, 5000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            return@withContext (respPacket != null && respPacket.type == "audio.ack")
        } catch (e: Exception) {
            appendLog("[WARN] Failed to set audio mute: ${e.message}")
            return@withContext false
        } finally {
            try { stream.abortReading(0) } catch (e: Exception) {}
        }
    }

    suspend fun setAudioDefaultSink(name: String): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext false
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(
            id = packetId,
            type = "audio.set_default_sink",
            body = mapOf("name" to name),
            hasPayload = false
        )
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream
        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close()
            val respBytes = ConnectionUtils.readWithDeadline(stream, 5000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            return@withContext (respPacket != null && respPacket.type == "audio.ack")
        } catch (e: Exception) {
            appendLog("[WARN] Failed to set default audio sink: ${e.message}")
            return@withContext false
        } finally {
            try { stream.abortReading(0) } catch (e: Exception) {}
        }
    }

    fun registerAudioTapAnnouncement(id: Long) {
        fileAnnouncements[id] = FileAnnouncement("__audio_tap__", 0L)
        appendLog("[AUDIO] Registered audio tap announcement with packetId=$id")
    }

    suspend fun startAudioTap(): Long? = withContext(Dispatchers.IO) {
        val conn = activeConnection ?: return@withContext null
        AudioStreamPlayer.startTap()
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "audio.tap_start", body = null, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream
        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close()
            val respBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            if (respPacket != null && respPacket.type == "audio.tap_ready") {
                val id = (respPacket.body?.get("id") as? Number)?.toLong() ?: run {
                    AudioStreamPlayer.stop()
                    return@withContext null
                }
                registerAudioTapAnnouncement(id)
                return@withContext id
            }
            AudioStreamPlayer.stop()
            return@withContext null
        } catch (e: Exception) {
            appendLog("[WARN] Failed to start audio tap: ${e.message}")
            AudioStreamPlayer.stop()
            return@withContext null
        } finally {
            try { stream.abortReading(0) } catch (e: Exception) {}
        }
    }

    suspend fun stopAudioTap(): Boolean = withContext(Dispatchers.IO) {
        val conn = activeConnection
        if (conn == null) {
            AudioStreamPlayer.stop()
            return@withContext false
        }
        val packetId = packetIdCounter.incrementAndGet()
        val packet = Packet(id = packetId, type = "audio.tap_stop", body = null, hasPayload = false)
        val stream = conn.createStream(true)
        val out = stream.outputStream
        val inp = stream.inputStream
        try {
            writeFramedBytes(out, encodePacket(packet))
            out.close()
            val respBytes = ConnectionUtils.readWithDeadline(stream, 5000) { readFramedBytes(inp) }
            val respPacket = decodePacket(respBytes)
            return@withContext (respPacket != null && respPacket.type == "audio.ack")
        } catch (e: Exception) {
            appendLog("[WARN] Failed to stop audio tap: ${e.message}")
            return@withContext false
        } finally {
            try { stream.abortReading(0) } catch (e: Exception) {}
            AudioStreamPlayer.stop()
        }
    }

    suspend fun sendFile(
        context: Context,
        uri: android.net.Uri,
        name: String,
        size: Long,
        kind: String? = null
    ) = withContext(Dispatchers.IO) {
        fileSendMutex.withLock {
            val conn = activeConnection
            if (conn == null) {
                appendLog("[ERROR] Cannot send file: Not connected to workstation")
                return@withLock
            }
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "hyprlink:filesend")
            wakeLock?.acquire(30 * 60 * 1000L) // máx 30 min durante transferências pesadas
            _fileSendingProgress.value = 0f
            _fileSendingStatus.value = "A preparar envio de $name..."
            val packetId = packetIdCounter.incrementAndGet()
            val startTime = android.os.SystemClock.elapsedRealtime()
            transferStartTimes[packetId] = startTime
            transferMeters[packetId] = TransferRateMeter()
            try {
                appendLog("[SHARE] Initiating file send: $name ($size bytes), packetId=$packetId")
                
                val item = TransferItem(
                    id = packetId,
                    name = name,
                    size = size,
                    direction = TransferDirection.UPLOAD,
                    status = TransferStatus.EM_CURSO,
                    progress = 0f,
                    bytesTransferred = 0L
                )
                updateTransfer(item)
                updateTransferNotification(context, item, force = true)

                // 1. Send share.file Packet on a control stream
                val packetBody = mutableMapOf<String, Any>(
                    "name" to name,
                    "size" to size
                )
                if (kind != null) {
                    packetBody["kind"] = kind
                }
                val packet = Packet(
                    id = packetId,
                    type = "share.file",
                    body = packetBody,
                    hasPayload = true
                )
                val controlStream = conn.createStream(true)
                val cborBytes = encodePacket(packet)
                val outCtrl = controlStream.outputStream
                writeFramedBytes(outCtrl, cborBytes)
                outCtrl.close() // half-close
                
                // Wait for EOF on control stream
                val inCtrl = controlStream.inputStream
                try { 
                    ConnectionUtils.readWithDeadline(controlStream, 10_000) {
                        inCtrl.readBytes()
                    }
                } catch (e: Exception) {}
                controlStream.abortReading(0)
                
                // 2. Open unidirectional stream for file payload
                val uniStream = conn.createStream(false)
                val outUni = uniStream.outputStream
                
                // Write 8-byte big-endian packet ID
                val idBytes = ByteArray(8)
                for (i in 0..7) {
                    idBytes[i] = ((packetId shr (56 - i * 8)) and 0xFF).toByte()
                }
                outUni.write(idBytes)
                
                val digest = MessageDigest.getInstance("SHA-256")
                
                // Stream chunks from file
                context.contentResolver.openInputStream(uri).use { inputStream ->
                    if (inputStream == null) throw IOException("Cannot open input stream for Uri: $uri")
                    val buffer = ByteArray(64 * 1024)
                    var bytesSent = 0L
                    var lastProgressUpdate = 0L
                    var lastFlushBytes = 0L
                    while (true) {
                        val read = inputStream.read(buffer)
                        if (read == -1) break
                        outUni.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        bytesSent += read

                        // Flush periódico dos dados a cada 512 KB
                        if (bytesSent - lastFlushBytes >= 512 * 1024L) {
                            outUni.flush()
                            lastFlushBytes = bytesSent
                        }

                        // Throttle nas emissões de UI / Notificações: a cada 250ms ou 1MB
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastProgressUpdate >= 250L || bytesSent == size) {
                            lastProgressUpdate = now
                            sampleTransfer(packetId, bytesSent, size)
                            val (bps, eta) = getTransferRateAndEta(packetId, bytesSent, size)
                            val progress = if (size > 0) bytesSent.toFloat() / size else 0f
                            
                            _fileSendingProgress.value = if (size > 0) progress else 1f
                            _fileSendingStatus.value = "A enviar: ${(progress * 100).toInt()}%"
                            
                            val currentItem = _transfers.value.find { it.id == packetId }
                            if (currentItem != null && currentItem.status == TransferStatus.EM_CURSO) {
                                val updated = currentItem.copy(
                                    bytesTransferred = bytesSent,
                                    progress = progress,
                                    bytesPerSec = bps,
                                    etaSeconds = eta
                                )
                                updateTransfer(updated)
                                updateTransferNotification(context, updated)
                            }
                        }
                    }
                }
                outUni.flush()
                outUni.close()
                
                val hashBytes = digest.digest()
                val localSha256 = hashBytes.joinToString("") { "%02x".format(it) }

                val durationMs = (android.os.SystemClock.elapsedRealtime() - startTime).coerceAtLeast(0L)
                val avgRate = TransferRateMeter.average(size, durationMs) ?: 0L
                transferMeters.remove(packetId)
                transferStartTimes.remove(packetId)
                
                val completedLocalItem = _transfers.value.find { it.id == packetId }
                if (completedLocalItem != null) {
                    val finalItem = completedLocalItem.copy(
                        sha256Local = localSha256,
                        status = TransferStatus.VERIFICADO,
                        progress = 1f,
                        bytesTransferred = size,
                        bytesPerSec = avgRate,
                        durationMs = durationMs
                    )
                    updateTransfer(finalItem)
                    notifyTransferSuccess(context, finalItem)
                }
                
                appendLog("[SHARE] Successfully sent file: $name. Calculated local SHA-256: $localSha256")
                appendLog("Ficheiro enviado: $name")
                _fileSendingStatus.value = "Ficheiro $name enviado com sucesso!"
            } catch (e: Exception) {
                transferMeters.remove(packetId)
                transferStartTimes.remove(packetId)
                cancelTransferNotification(context, packetId)
                appendLog("[SHARE] Error sending file: ${e.message}")
                _fileSendingStatus.value = "Erro ao enviar: ${e.message}"
                
                val currentItem = _transfers.value.find { it.id == packetId }
                if (currentItem != null) {
                    updateTransfer(currentItem.copy(
                        status = TransferStatus.ERRO,
                        error = e.message ?: "Erro desconhecido no envio"
                    ))
                }
            } finally {
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock.release()
                    }
                } catch (ignored: Exception) {}
                delay(2500)
                _fileSendingProgress.value = null
                _fileSendingStatus.value = null
            }
        }
    }

    // --- INCOMING PEER-INITIATED STREAM HANDLER ---

    private suspend fun handlePeerStream(stream: QuicStream, context: Context) = withContext(Dispatchers.IO) {
        val inp = stream.inputStream
        try {
            val cborBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readFramedBytes(inp) }
            val packet = decodePacket(cborBytes) ?: return@withContext
            
            val routerPrefix = packet.type.substringBefore(".")
            appendLog("[RECV] Incoming stream packet type: ${packet.type} (routerPrefix: $routerPrefix)")

            when (routerPrefix) {
                "clipboard" -> {
                    if (packet.type == "clipboard.set") {
                        val mime = packet.body?.get("mime") as? String
                        if (mime == "image/png" && packet.hasPayload) {
                            val sizeVal = packet.body?.get("size")
                            val size = when (sizeVal) {
                                is Number -> sizeVal.toLong()
                                else -> 0L
                            }
                            fileAnnouncements[packet.id] = FileAnnouncement("__clipboard_image__", size)
                            appendLog("[CLIP] Registered clipboard image announcement with packetId=${packet.id}, size=$size")
                        } else {
                            val text = packet.body?.get("text") as? String
                            if (text != null) {
                                setClipboardText(context, text)
                            }
                        }
                    }
                }
                "notification" -> {
                    if (packet.type == "notification.send") {
                        val title = packet.body?.get("title") as? String ?: "Workstation Notification"
                        val body = packet.body?.get("body") as? String ?: ""
                        val appName = packet.body?.get("app_name") as? String ?: "System"
                        postNotification(context, title, body, appName)
                    } else if (packet.type == "notification.action") {
                        val key = packet.body?.get("key") as? String
                        val actionValue = packet.body?.get("action") ?: packet.body?.get("idx")
                        if (key != null && actionValue != null) {
                            val sbn = HyprNotificationListenerService.cachedNotifications[key]
                            if (sbn != null) {
                                val actions = sbn.notification.actions
                                if (actions != null) {
                                    val idx = when (actionValue) {
                                        is Number -> actionValue.toInt()
                                        is String -> actionValue.toIntOrNull() ?: -1
                                        else -> -1
                                    }
                                    
                                    var triggeredAction: android.app.Notification.Action? = null
                                    var triggeredIdx = -1
                                    
                                    if (idx >= 0 && idx < actions.size) {
                                        triggeredAction = actions[idx]
                                        triggeredIdx = idx
                                    } else if (actionValue is String) {
                                        val match = actions.withIndex().find { 
                                            it.value.title?.toString()?.equals(actionValue, ignoreCase = true) == true 
                                        }
                                        if (match != null) {
                                            triggeredAction = match.value
                                            triggeredIdx = match.index
                                        }
                                    }
                                    
                                    if (triggeredAction != null) {
                                        try {
                                            triggeredAction.actionIntent.send()
                                            appendLog("[NOTIF] Triggered action $triggeredIdx ('${triggeredAction.title}') for key $key")
                                        } catch (e: Exception) {
                                            appendLog("[WARN] Failed to trigger action: ${e.message}")
                                        }
                                    } else {
                                        appendLog("[WARN] Action '$actionValue' not resolved for key $key")
                                    }
                                }
                            } else {
                                appendLog("[WARN] Notification key $key not found in active cache for action")
                            }
                        }
                    } else if (packet.type == "notification.reply") {
                        val replyReq = NotificationReplyLogic.parseNotificationReply(packet.body)
                        if (replyReq == null) {
                            appendLog("[NOTIF] Pacote notification.reply com corpo inválido ou incompleto")
                        } else {
                            executeNotificationReply(context, replyReq)
                        }
                    } else if (packet.type == "notification.dismiss") {
                        val key = packet.body?.get("key") as? String
                        if (key != null) {
                            val listener = HyprNotificationListenerService.instance
                            if (listener != null) {
                                try {
                                    listener.cancelNotification(key)
                                    appendLog("[NOTIF] Dismissed notification locally for key $key")
                                } catch (e: Exception) {
                                    appendLog("[WARN] Failed to dismiss notification: ${e.message}")
                                }
                            } else {
                                appendLog("[WARN] HyprNotificationListenerService instance not running")
                            }
                        }
                    }
                }
                "media" -> {
                    if (packet.type == "media.state") {
                        handleMediaState(packet.body)
                    }
                }
                "battery" -> {
                    if (packet.type == "battery.state") {
                        handleBatteryState(packet.body)
                    }
                }
                "pc" -> {
                    if (packet.type == "pc.status") {
                        handlePcStatus(packet.body)
                    } else if (packet.type == "pc.addrs") {
                        handlePcAddrs(packet.body)
                    }
                }
                "hypr" -> {
                    if (packet.type == "hypr.event") {
                        val eventName = packet.body?.get("event") as? String ?: ""
                        appendLog("[HYPR] Received workstation event: $eventName")
                        _hyprEvents.tryEmit(eventName)
                    }
                }
                "share" -> {
                    if (packet.type == "share.file") {
                        val name = packet.body?.get("name") as? String
                        val sizeVal = packet.body?.get("size")
                        val size = when (sizeVal) {
                            is Number -> sizeVal.toLong()
                            else -> 0L
                        }
                        if (name != null) {
                            fileAnnouncements[packet.id] = FileAnnouncement(name, size)
                            appendLog("[SHARE] Received file announcement: $name ($size bytes), packetId=${packet.id}")
                        }
                    } else if (packet.type == "share.progress") {
                        val idVal = packet.body?.get("id")
                        val id = when (idVal) {
                            is Number -> idVal.toLong()
                            else -> 0L
                        }
                        val bytesVal = packet.body?.get("bytes")
                        val bytes = when (bytesVal) {
                            is Number -> bytesVal.toLong()
                            else -> 0L
                        }
                        val totalVal = packet.body?.get("total")
                        val total = when (totalVal) {
                            is Number -> totalVal.toLong()
                            else -> 0L
                        }
                        handleShareProgress(id, bytes, total)
                    } else if (packet.type == "share.done") {
                        val idVal = packet.body?.get("id")
                        val id = when (idVal) {
                            is Number -> idVal.toLong()
                            else -> 0L
                        }
                        val ok = packet.body?.get("ok") as? Boolean ?: false
                        val sha256 = packet.body?.get("sha256") as? String ?: ""
                        val bytesVal = packet.body?.get("bytes")
                        val bytes = when (bytesVal) {
                            is Number -> bytesVal.toLong()
                            else -> 0L
                        }
                        val error = packet.body?.get("error") as? String
                        val name = packet.body?.get("name") as? String
                        handleShareDone(id, ok, sha256, bytes, error, context, name)
                    }
                }
                "webcam" -> {
                    if (packet.type == "webcam.start") {
                        val width = (packet.body?.get("width") as? Number)?.toInt() ?: 1280
                        val height = (packet.body?.get("height") as? Number)?.toInt() ?: 720
                        val fps = (packet.body?.get("fps") as? Number)?.toInt() ?: 24
                        val codec = (packet.body?.get("codec") as? String) ?: "h264"
                        val conn = activeConnection
                        if (conn != null) {
                            if (isAppInForeground()) {
                                WebcamStreamer.start(context, conn, packet.id, width, height, fps, codec)
                            } else {
                                postWebcamStartNotification(context, packet.id, width, height, fps, codec)
                                appendLog("[WEBCAM] Pedido recebido em background — notificação exibida ao utilizador")
                            }
                        }
                    } else if (packet.type == "webcam.configure") {
                        val parsed = WebcamLogic.parseWebcamConfig(
                            body = packet.body,
                            defaultWidth = WebcamStreamer.currentWidth.value,
                            defaultHeight = WebcamStreamer.currentHeight.value,
                            defaultFps = WebcamStreamer.currentFps.value,
                            defaultCodec = WebcamStreamer.activeCodec.value.ifBlank { "h264" }
                        )

                        appendLog("[WEBCAM] Recebido webcam.configure: ${parsed.width}x${parsed.height} @ ${parsed.fps}fps (${parsed.codec})")
                        if (WebcamStreamer.isStreaming.value) {
                            WebcamStreamer.restartWithResolution(context, parsed.width, parsed.height, parsed.fps, parsed.codec)
                        } else {
                            WebcamStreamer.setPendingConfig(parsed.width, parsed.height, parsed.fps, parsed.codec)
                            appendLog("[WEBCAM] Câmara inativa — formato guardado como pendente (respeitando regra de background)")
                        }
                    } else if (packet.type == "webcam.stop") {
                        WebcamStreamer.stop()
                    } else if (packet.type == "webcam.mic_start_request") {
                        if (isAppInForeground()) {
                            WebcamStreamer.startMic(context)
                        } else {
                            postMicStartNotification(context)
                            appendLog("[WEBCAM] Pedido de microfone recebido em background — notificação exibida ao utilizador")
                        }
                    } else if (packet.type == "webcam.mic_stop_request") {
                        WebcamStreamer.stopMic()
                    }
                }
                "phone_audio" -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

                    if (packet.type == "phone_audio.state") {
                        fun pct(streamType: Int): Int {
                            val cur = am.getStreamVolume(streamType)
                            val max = am.getStreamMaxVolume(streamType).coerceAtLeast(1)
                            return (cur * 100 / max)
                        }
                        val ringerMode = when (am.ringerMode) {
                            android.media.AudioManager.RINGER_MODE_SILENT -> "silent"
                            android.media.AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                            else -> "normal"
                        }
                        val body = mapOf(
                            "ring_percent" to pct(android.media.AudioManager.STREAM_RING),
                            "media_percent" to pct(android.media.AudioManager.STREAM_MUSIC),
                            "alarm_percent" to pct(android.media.AudioManager.STREAM_ALARM),
                            "ringer_mode" to ringerMode,
                            "dnd_access" to nm.isNotificationPolicyAccessGranted,
                            "dnd_enabled" to (nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                        )
                        val replyPacket = Packet(
                            id = packet.id,
                            type = "phone_audio.state_reply",
                            body = body,
                            hasPayload = false
                        )
                        val out = stream.outputStream
                        writeFramedBytes(out, encodePacket(replyPacket))
                        out.close()
                    } else if (packet.type == "phone_audio.set_volume") {
                        val streamName = packet.body?.get("stream") as? String
                        val percent = (packet.body?.get("percent") as? Number)?.toInt()?.coerceIn(0, 100)
                        val streamType = when (streamName) {
                            "ring" -> android.media.AudioManager.STREAM_RING
                            "media" -> android.media.AudioManager.STREAM_MUSIC
                            "alarm" -> android.media.AudioManager.STREAM_ALARM
                            else -> null
                        }
                        if (streamType != null && percent != null) {
                            val max = am.getStreamMaxVolume(streamType)
                            am.setStreamVolume(streamType, (percent * max / 100), 0)
                        }
                    } else if (packet.type == "phone_audio.set_ringer_mode") {
                        val mode = packet.body?.get("mode") as? String
                        if (nm.isNotificationPolicyAccessGranted) {
                            try {
                                am.ringerMode = when (mode) {
                                    "silent" -> android.media.AudioManager.RINGER_MODE_SILENT
                                    "vibrate" -> android.media.AudioManager.RINGER_MODE_VIBRATE
                                    else -> android.media.AudioManager.RINGER_MODE_NORMAL
                                }
                            } catch (e: SecurityException) { /* sem permissão de verdade, ignora */ }
                        } else {
                            promptDndPermission(context)
                        }
                    } else if (packet.type == "phone_audio.set_dnd") {
                        val enabled = packet.body?.get("enabled") as? Boolean ?: false
                        if (nm.isNotificationPolicyAccessGranted) {
                            try {
                                nm.setInterruptionFilter(
                                    if (enabled) android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                                    else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                                )
                            } catch (e: SecurityException) { /* sem permissão de verdade, ignora */ }
                        } else {
                            promptDndPermission(context)
                        }
                    }
                }
                "phone" -> {
                    if (packet.type == "phone.open_url") {
                        val url = packet.body?.get("url") as? String
                        if (!url.isNullOrBlank()) {
                            handlePhoneOpenUrl(context, url)
                        } else {
                            appendLog("[HANDOFF] URL inválido (vazio)")
                        }
                    } else if (packet.type == "phone.run_app") {
                        val pkg = packet.body?.get("package") as? String
                        if (!pkg.isNullOrBlank()) {
                            handlePhoneRunApp(context, pkg)
                        } else {
                            appendLog("[HANDOFF] package desconhecido: (vazio)")
                        }
                    } else if (packet.type == "phone.media") {
                        val action = packet.body?.get("action") as? String
                        handlePhoneMediaAction(context, action)
                    } else if (packet.type == "phone.find") {
                        handlePhoneFind(context, packet.body)
                    } else if (packet.type == "phone.find_stop") {
                        handlePhoneFindStop(context)
                    }
                }
                "shortcuts", "shortcut" -> {
                    if (packet.type == "shortcuts.list") {
                        handleShortcutsList(packet.body)
                    }
                }
                else -> {
                    appendLog("[WARN] Unknown packet prefix router: $routerPrefix (type: ${packet.type})")
                }
            }
        } catch (e: Exception) {
            appendLog("[WARN] Error handling peer-initiated stream: ${e.message}")
        } finally {
            try {
                stream.abortReading(0)
            } catch (e: Exception) {}
            try {
                stream.outputStream.close()
            } catch (e: Exception) {}
        }
    }

    private suspend fun handlePeerUniStream(stream: QuicStream, context: Context) = withContext(Dispatchers.IO) {
        val inp = stream.inputStream
        var handedOffToAudioTap = false
        try {
            // Read 8 bytes for packet ID
            val idBytes = ConnectionUtils.readWithDeadline(stream, 10_000) { readExact(inp, 8) }
            var packetId = 0L
            for (i in 0..7) {
                packetId = (packetId shl 8) or (idBytes[i].toLong() and 0xFF)
            }
            appendLog("[SHARE] Received unidirectional stream with packetId=$packetId")

            // Wait for announcement if not present yet (out-of-order delivery)
            var announcement: FileAnnouncement? = null
            for (retry in 1..40) { // 40 * 200ms = 8 seconds
                announcement = fileAnnouncements[packetId]
                if (announcement != null) break
                delay(200)
            }

            if (announcement == null) {
                appendLog("[ERROR] Timeout waiting for file announcement with packetId=$packetId")
                return@withContext
            }

            // Remove announcement to avoid leaks
            fileAnnouncements.remove(packetId)

            if (announcement.name == "__audio_tap__") {
                appendLog("[AUDIO] Redirecting unidirectional stream for audio tap to AudioStreamPlayer...")
                handedOffToAudioTap = true
                AudioStreamPlayer.playStream(stream)
                return@withContext
            }

            if (announcement.name == "__clipboard_image__") {
                appendLog("[CLIP] Receiving clipboard image stream for packetId=$packetId (size=${announcement.size} bytes)...")
                val clipDir = File(context.cacheDir, "clip")
                if (!clipDir.exists()) {
                    clipDir.mkdirs()
                }
                val clipFile = File(clipDir, "clipboard.png")
                val buffer = ByteArray(16 * 1024)
                var totalRead = 0L
                val maxLimit = 32L * 1024 * 1024 // 32 MiB

                FileOutputStream(clipFile).use { fos ->
                    while (true) {
                        val read = inp.read(buffer)
                        if (read == -1) break
                        if (totalRead + read > maxLimit) {
                            throw IOException("Clipboard image exceeded 32 MiB limit")
                        }
                        fos.write(buffer, 0, read)
                        totalRead += read
                    }
                }

                appendLog("[CLIP] imagem recebida do PC: ${totalRead / 1024} KB")
                setClipboardImage(context, clipFile)
                return@withContext
            }

            val rawName = announcement.name
            val size = announcement.size

            // 4. Sanitiza name (usa só o último componente do path; rejeita vazio, "." e "..")
            val sanitizedName = sanitizeFileName(rawName)
            if (sanitizedName == null) {
                appendLog("[ERROR] Sanitization failed for filename: $rawName")
                return@withContext
            }

            appendLog("[SHARE] Starting file receive for $sanitizedName ($size bytes)...")
            saveFileFromStream(stream, sanitizedName, size, context, packetId)

        } catch (e: Exception) {
            appendLog("[ERROR] Error receiving file: ${e.message}")
        } finally {
            if (!handedOffToAudioTap) {
                try {
                    stream.abortReading(0)
                } catch (e: Exception) {}
            }
        }
    }

    private fun sanitizeFileName(name: String): String? {
        val normalized = name.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        val trimmed = fileName.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "..") {
            return null
        }
        if (trimmed.contains("/") || trimmed.contains("..")) {
            return null
        }
        return trimmed
    }

    fun handleShareProgress(id: Long, bytes: Long, total: Long) {
        val currentItem = _transfers.value.find { it.id == id } ?: return
        if (currentItem.status == TransferStatus.EM_CURSO) {
            sampleTransfer(id, bytes, total)
            val (bps, eta) = getTransferRateAndEta(id, bytes, total)
            val progress = if (total > 0) bytes.toFloat() / total else 0f
            if (progress >= 1f || (total > 0 && bytes >= total)) {
                val startTime = transferStartTimes.remove(id) ?: currentItem.timestamp
                val durationMs = (android.os.SystemClock.elapsedRealtime() - startTime).coerceAtLeast(0L)
                val avgRate = TransferRateMeter.average(total, durationMs) ?: 0L
                transferMeters.remove(id)
                val updated = currentItem.copy(
                    bytesTransferred = total,
                    progress = 1f,
                    status = TransferStatus.VERIFICADO,
                    bytesPerSec = avgRate,
                    durationMs = durationMs
                )
                updateTransfer(updated)
                appContext?.let { ctx -> notifyTransferSuccess(ctx, updated) }
            } else {
                val updated = currentItem.copy(
                    bytesTransferred = bytes,
                    progress = progress,
                    bytesPerSec = bps,
                    etaSeconds = eta
                )
                updateTransfer(updated)
                appContext?.let { ctx -> updateTransferNotification(ctx, updated) }
            }
        }
    }

    fun handleShareDone(
        id: Long,
        ok: Boolean,
        sha256: String,
        bytes: Long,
        error: String?,
        context: Context,
        name: String? = null
    ) {
        val currentItem = _transfers.value.find { it.id == id }
        if (currentItem == null) {
            appendLog("[WARN] Received share.done for unknown transfer id=$id")
            return
        }
        
        if (currentItem.direction == TransferDirection.UPLOAD) {
            val strings = LanguageManager.currentStrings
            if (!ok) {
                transferMeters.remove(id)
                transferStartTimes.remove(id)
                cancelTransferNotification(context, id)
                val friendlyError = ShareErrorMapper.mapShareError(error, strings)
                updateTransfer(currentItem.copy(
                    status = TransferStatus.ERRO,
                    error = friendlyError
                ))
                appendLog("[SHARE] Transfer id=$id failed: $friendlyError (raw: $error)")
                postNotification(context, strings.transferRejectedTitle, "${currentItem.name}: $friendlyError", "HyprLink")
                return
            }
            val localHash = currentItem.sha256Local ?: ""
            if (localHash.isEmpty() || localHash.equals(sha256, ignoreCase = true)) {
                val startTime = transferStartTimes.remove(id) ?: currentItem.timestamp
                val durationMs = currentItem.durationMs ?: (android.os.SystemClock.elapsedRealtime() - startTime).coerceAtLeast(0L)
                val avgRate = currentItem.bytesPerSec ?: (TransferRateMeter.average(bytes, durationMs) ?: 0L)
                transferMeters.remove(id)

                val updated = currentItem.copy(
                    status = TransferStatus.VERIFICADO,
                    progress = 1f,
                    bytesTransferred = bytes,
                    sha256Remote = sha256,
                    savedNameOnPc = name,
                    bytesPerSec = avgRate,
                    durationMs = durationMs
                )
                updateTransfer(updated)
                val savedLog = if (name != null) " - Guardado no PC como $name" else ""
                appendLog("[SHARE] Transfer id=$id VERIFIED ✓ (SHA256: $sha256)$savedLog")
                notifyTransferSuccess(context, updated)
            } else {
                transferMeters.remove(id)
                transferStartTimes.remove(id)
                cancelTransferNotification(context, id)
                updateTransfer(currentItem.copy(
                    status = TransferStatus.ERRO,
                    progress = 1f,
                    bytesTransferred = bytes,
                    sha256Remote = sha256,
                    error = "ERROR: hash mismatch"
                ))
                appendLog("[SHARE] Transfer id=$id ERROR: SHA256 mismatch. Local: $localHash, Remote: $sha256")
                postNotification(context, strings.transferRejectedTitle, "${currentItem.name}: erro de verificação (hash mismatch)", "HyprLink")
            }
        } else {
            val session = activeDownloads[id]
            if (session != null) {
                session.doneDeferred.complete(ShareDonePacket(id, ok, sha256, bytes, error, name))
            } else {
                appendLog("[WARN] Received share.done for download id=$id but session not found or already completed")
            }
        }
    }

    private suspend fun saveFileFromStream(
        stream: QuicStream,
        name: String,
        size: Long,
        context: Context,
        packetId: Long
    ) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/HyprLink")
        }
        
        val externalUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        
        val uri: android.net.Uri? = resolver.insert(externalUri, contentValues)
        if (uri == null) {
            throw IOException("Failed to create MediaStore entry for $name")
        }

        val startTime = android.os.SystemClock.elapsedRealtime()
        transferStartTimes[packetId] = startTime
        transferMeters[packetId] = TransferRateMeter()

        val item = TransferItem(
            id = packetId,
            name = name,
            size = size,
            direction = TransferDirection.DOWNLOAD,
            status = TransferStatus.EM_CURSO,
            progress = 0f,
            bytesTransferred = 0L,
            uri = uri
        )
        updateTransfer(item)
        updateTransferNotification(context, item, force = true)

        val doneDeferred = CompletableDeferred<ShareDonePacket>()
        val session = DownloadSession(uri = uri, doneDeferred = doneDeferred)
        activeDownloads[packetId] = session

        val inputStream = stream.inputStream
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            _fileSendingProgress.value = 0f
            _fileSendingStatus.value = "A receber $name..."

            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream == null) throw IOException("Failed to open output stream for Uri: $uri")
                val buffer = ByteArray(32 * 1024)
                var bytesReceived = 0L
                while (true) {
                    val read = ConnectionUtils.readWithDeadline(stream, 10_000) {
                        inputStream.read(buffer)
                    }
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    bytesReceived += read

                    sampleTransfer(packetId, bytesReceived, size)
                    val (bps, eta) = getTransferRateAndEta(packetId, bytesReceived, size)
                    val progress = if (size > 0) bytesReceived.toFloat() / size else 0f
                    
                    _fileSendingProgress.value = if (size > 0) progress else 1f
                    _fileSendingStatus.value = "A receber: ${(progress * 100).toInt()}%"
                    
                    val currentItem = _transfers.value.find { it.id == packetId }
                    if (currentItem != null) {
                        if (bytesReceived > currentItem.bytesTransferred || bps != currentItem.bytesPerSec) {
                            val updated = currentItem.copy(
                                bytesTransferred = bytesReceived,
                                progress = progress,
                                bytesPerSec = bps,
                                etaSeconds = eta
                            )
                            updateTransfer(updated)
                            updateTransferNotification(context, updated)
                        }
                    }
                }
            }

            val localSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            session.localSha256 = localSha256
            session.isStreamFinished = true

            appendLog("[SHARE] Stream EOF reached for download id=$packetId. Calculated local SHA-256: $localSha256. Waiting up to 15s for share.done...")

            try {
                withTimeout(15000) {
                    val donePacket = doneDeferred.await()
                    if (donePacket.ok) {
                        if (localSha256.equals(donePacket.sha256, ignoreCase = true)) {
                            val durationMs = (android.os.SystemClock.elapsedRealtime() - startTime).coerceAtLeast(0L)
                            val avgRate = TransferRateMeter.average(donePacket.bytes, durationMs) ?: 0L
                            transferMeters.remove(packetId)
                            transferStartTimes.remove(packetId)

                            val verifiedItem = TransferItem(
                                id = packetId,
                                name = name,
                                size = size,
                                direction = TransferDirection.DOWNLOAD,
                                status = TransferStatus.VERIFICADO,
                                progress = 1f,
                                bytesTransferred = donePacket.bytes,
                                sha256Local = localSha256,
                                sha256Remote = donePacket.sha256,
                                uri = uri,
                                bytesPerSec = avgRate,
                                durationMs = durationMs
                            )
                            updateTransfer(verifiedItem)
                            appendLog("[SHARE] Download id=$packetId VERIFIED ✓ (SHA256: $localSha256)")
                            notifyTransferSuccess(context, verifiedItem)
                        } else {
                            transferMeters.remove(packetId)
                            transferStartTimes.remove(packetId)
                            cancelTransferNotification(context, packetId)
                            try {
                                resolver.delete(uri, null, null)
                            } catch (e: Exception) {
                                appendLog("[SHARE] Failed to delete mismatched file: ${e.message}")
                            }
                            updateTransfer(TransferItem(
                                id = packetId,
                                name = name,
                                size = size,
                                direction = TransferDirection.DOWNLOAD,
                                status = TransferStatus.ERRO,
                                progress = 1f,
                                bytesTransferred = donePacket.bytes,
                                sha256Local = localSha256,
                                sha256Remote = donePacket.sha256,
                                error = "ERROR: hash mismatch",
                                uri = uri
                            ))
                            appendLog("[SHARE] Download id=$packetId ERROR: SHA256 mismatch. Local: $localSha256, Remote: ${donePacket.sha256}. Deleted file.")
                            postNotification(context, "Verification Error", "File $name was corrupted or modified during transfer.", "HyprLink")
                        }
                    } else {
                        transferMeters.remove(packetId)
                        transferStartTimes.remove(packetId)
                        cancelTransferNotification(context, packetId)
                        try {
                            resolver.delete(uri, null, null)
                        } catch (e: Exception) {}
                        val errMsg = donePacket.error ?: "Transferência falhou no PC"
                        updateTransfer(TransferItem(
                            id = packetId,
                            name = name,
                            size = size,
                            direction = TransferDirection.DOWNLOAD,
                            status = TransferStatus.ERRO,
                            progress = 1f,
                            bytesTransferred = donePacket.bytes,
                            error = errMsg,
                            uri = uri
                        ))
                        appendLog("[SHARE] Download id=$packetId failed on PC side: $errMsg")
                        postNotification(context, "Transfer Failed", "PC reported an error processing the file.", "HyprLink")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                transferMeters.remove(packetId)
                transferStartTimes.remove(packetId)
                cancelTransferNotification(context, packetId)
                updateTransfer(TransferItem(
                    id = packetId,
                    name = name,
                    size = size,
                    direction = TransferDirection.DOWNLOAD,
                    status = TransferStatus.NAO_VERIFICADO,
                    progress = 1f,
                    bytesTransferred = size,
                    sha256Local = localSha256,
                    error = "Unverified (timeout)",
                    uri = uri
                ))
                appendLog("[SHARE] Download id=$packetId timeout waiting for share.done. Kept file, marked NAO_VERIFICADO.")
                postNotification(context, "File Saved (Unverified)", "$name was saved but could not be verified.", "HyprLink")
            }

        } catch (e: Exception) {
            transferMeters.remove(packetId)
            transferStartTimes.remove(packetId)
            cancelTransferNotification(context, packetId)
            try {
                resolver.delete(uri, null, null)
            } catch (ex: Exception) {}
            
            updateTransfer(TransferItem(
                id = packetId,
                name = name,
                size = size,
                direction = TransferDirection.DOWNLOAD,
                status = TransferStatus.ERRO,
                progress = 1f,
                bytesTransferred = 0L,
                error = e.message ?: "Unknown reception error",
                uri = uri
            ))
            throw e
        } finally {
            activeDownloads.remove(packetId)
            _fileSendingProgress.value = null
            _fileSendingStatus.value = null
        }
    }

    internal fun executeNotificationReply(context: Context, req: NotificationReplyRequest) {
        val sbn = HyprNotificationListenerService.cachedNotifications[req.key]
            ?: HyprNotificationListenerService.instance?.activeNotifications?.find { it.key == req.key }
        if (sbn == null) {
            appendLog("[NOTIF] Notificação já não existe para resposta do PC (chave: ${req.key})")
            return
        }

        val actions = sbn.notification?.actions
        val targetAction = if (req.idx != null && req.idx in 0 until (actions?.size ?: 0)) {
            actions?.get(req.idx)
        } else {
            actions?.find { !it.remoteInputs.isNullOrEmpty() }
        }

        if (targetAction == null) {
            appendLog("[NOTIF] Ação de resposta não encontrada para a chave ${req.key} (idx=${req.idx})")
            return
        }

        val remoteInputs = targetAction.remoteInputs
        if (remoteInputs.isNullOrEmpty()) {
            appendLog("[NOTIF] Ação não suporta resposta de texto (sem RemoteInput) para a chave ${req.key}")
            return
        }

        val fillIntent = Intent()
        val results = Bundle()
        for (ri in remoteInputs) {
            results.putCharSequence(ri.resultKey, req.text)
        }
        android.app.RemoteInput.addResultsToIntent(remoteInputs, fillIntent, results)
        try {
            val listenerContext = HyprNotificationListenerService.instance ?: context
            targetAction.actionIntent.send(listenerContext, 0, fillIntent)
            appendLog("[NOTIF] Resposta enviada com sucesso para a notificação (chave: ${req.key})")
        } catch (e: Exception) {
            appendLog("[NOTIF] Falha ao enviar resposta para a notificação: ${e.message}")
        }
    }

    private fun handleBatteryState(body: Map<String, Any?>?) {
        if (body == null) return
        val levelVal = body["level"]
        val chargingVal = body["charging"]
        val pluggedVal = body["plugged"]
        val presentVal = body["present"]

        val level = when (levelVal) {
            is Number -> levelVal.toInt()
            else -> null
        }
        val charging = when (chargingVal) {
            is Boolean -> chargingVal
            else -> false
        }
        val plugged = when (pluggedVal) {
            is Boolean -> pluggedVal
            else -> false
        }
        val present = when (presentVal) {
            is Boolean -> presentVal
            else -> true
        }

        if (level != null) {
            _batteryLevel.value = level
            _pcBattery.value = PcBattery(level = level, charging = charging, plugged = plugged, present = present)
            appendLog("[BATTERY] Workstation Battery: $level% (Charging: $charging, Plugged: $plugged, Present: $present)")
        } else if (!present) {
            _pcBattery.value = PcBattery(level = 0, charging = false, plugged = false, present = false)
            appendLog("[BATTERY] Workstation Battery: not present")
        }
        if (chargingVal is Boolean) {
            _batteryCharging.value = chargingVal
        }
    }

    private fun handlePcStatus(body: Map<String, Any?>?) {
        if (body == null) return
        val hostname = (body["hostname"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val cpuPct = body["cpu_pct"]?.let {
            when (it) {
                is Number -> it.toFloat()
                is String -> it.toFloatOrNull()
                else -> null
            }
        }
        val ramUsed = body["ram_used_b"]?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        }
        val ramTotal = body["ram_total_b"]?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        }
        val cpuTemp = body["cpu_temp_c"]?.let {
            when (it) {
                is Number -> it.toFloat()
                is String -> it.toFloatOrNull()
                else -> null
            }
        }
        val uptime = body["uptime_s"]?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        }
        val rtt = body["rtt_ms"]?.let {
            when (it) {
                is Number -> it.toFloat()
                is String -> it.toFloatOrNull()
                else -> null
            }
        }
        val diskFree = body["disk_free_b"]?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        }
        val diskTotal = body["disk_total_b"]?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        }
        val gpuPct = body["gpu_pct"]?.let {
            when (it) {
                is Number -> it.toFloat()
                is String -> it.toFloatOrNull()
                else -> null
            }
        }

        _pcStatus.value = PcStatus(
            hostname = hostname,
            cpuPct = cpuPct,
            ramUsedBytes = ramUsed,
            ramTotalBytes = ramTotal,
            cpuTempC = cpuTemp,
            uptimeSeconds = uptime,
            rttMs = rtt,
            diskFreeBytes = diskFree,
            diskTotalBytes = diskTotal,
            gpuPct = gpuPct
        )
    }

    private fun handlePcAddrs(body: Map<String, Any?>?) {
        val addrs = UsbConnectionLogic.parsePcAddrs(body)
        val stId = currentStationId
        if (stId != null) {
            updateStationPcAddrs(stId, addrs)
        }
        appendLog("[USB] Recebidos ${addrs.size} endereços do PC: ${addrs.map { "${it.kind}:${it.ip}" }}")
    }

    private fun handleMediaState(body: Map<String, Any?>?) {
        if (body == null) return
        val player = body["player"] as? String ?: "Unknown Player"
        val status = body["status"] as? String ?: "stopped"
        val title = body["title"] as? String
        val artist = body["artist"] as? String
        val album = body["album"] as? String
        val posMs = (body["position_ms"] as? Number)?.toLong()
        val lenMs = (body["length_ms"] as? Number)?.toLong()
        val artBytes: ByteArray? = when (val rawArt = body["art"]) {
            is ByteArray -> rawArt
            is List<*> -> rawArt.mapNotNull { (it as? Number)?.toByte() }.toByteArray()
            is String -> try { android.util.Base64.decode(rawArt, android.util.Base64.DEFAULT) } catch (_: Exception) { null }
            else -> null
        }
        val artId = body["art_id"] as? String

        _mediaState.value = MediaState(
            player = player,
            status = status,
            title = title,
            artist = artist,
            album = album,
            positionMs = posMs,
            lengthMs = lenMs,
            artBytes = artBytes,
            artId = artId
        )
        appendLog("[MEDIA] Now Playing updated: $title - $artist [$status]${if (artId != null) " (art_id=$artId)" else ""}")
    }

    private fun setClipboardImage(context: Context, file: File) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.clip",
                    file
                )
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newUri(context.contentResolver, "HyprLink", uri)
                clipboard.setPrimaryClip(clip)
                lastReceivedClipboardText = "[Imagem: ${file.length() / 1024} KB]"
                appendLog("[CLIP] Imagem colocada no clipboard Android via FileProvider")
            } catch (e: Exception) {
                appendLog("[CLIP] Erro ao colocar imagem no clipboard: ${e.message}")
            }
        }
    }

    private fun setClipboardText(context: Context, text: String) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("HyprLink Workstation Clipboard", text)
                lastReceivedClipboardText = text
                clipboard.setPrimaryClip(clip)
                appendLog("[CLIPBOARD] Copied from workstation!")
            } catch (e: Exception) {
                appendLog("[CLIPBOARD] Error setting clipboard: ${e.message}")
            }
        }
    }

    private const val REMOTE_OPEN_CHANNEL_ID = "remote-open"

    private fun ensureRemoteOpenNotificationChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(REMOTE_OPEN_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                REMOTE_OPEN_CHANNEL_ID,
                "Abertura Remota",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications to open links or apps requested by PC"
                enableLights(true)
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun handlePhoneOpenUrl(context: Context, url: String) {
        val uri = try {
            val parsed = Uri.parse(url)
            if (parsed.scheme.isNullOrEmpty()) {
                Uri.parse("https://$url")
            } else {
                parsed
            }
        } catch (e: Exception) {
            appendLog("[HANDOFF] URL inválido: $url")
            return
        }

        if (uri == null || uri.scheme.isNullOrEmpty()) {
            appendLog("[HANDOFF] URL inválido: $url")
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val isForeground = try {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        } catch (e: Exception) {
            false
        }

        if (isForeground) {
            try {
                context.startActivity(intent)
                appendLog("[HANDOFF] URL aberto diretamente: $url")
            } catch (e: Exception) {
                postRemoteOpenNotification(context, "Open Link", url, intent)
                appendLog("[HANDOFF] abertura diferida pra notificação (background)")
            }
        } else {
            postRemoteOpenNotification(context, "Open Link", url, intent)
            appendLog("[HANDOFF] abertura diferida pra notificação (background)")
        }
    }

    private fun handlePhoneRunApp(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent == null) {
            appendLog("[HANDOFF] package desconhecido: $pkg")
            postUnknownPackageNotification(context, pkg)
            return
        }

        val isForeground = try {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        } catch (e: Exception) {
            false
        }

        if (isForeground) {
            try {
                context.startActivity(intent)
                appendLog("[HANDOFF] Aplicação aberta diretamente: $pkg")
            } catch (e: Exception) {
                postRemoteOpenNotification(context, "Open App", pkg, intent)
                appendLog("[HANDOFF] abertura diferida pra notificação (background)")
            }
        } else {
            postRemoteOpenNotification(context, "Open App", pkg, intent)
            appendLog("[HANDOFF] abertura diferida pra notificação (background)")
        }
    }

    private fun handlePhoneMediaAction(context: Context, action: String?) {
        val controller = PhoneStatusCollector.getActiveMediaController(context)
        if (controller == null) {
            appendLog("[MEDIA] phone.media sem sessão ativa")
            return
        }

        try {
            val controls = controller.transportControls
            when (action) {
                "previous" -> {
                    appendLog("[MEDIA] Executando phone.media: previous (${controller.packageName})")
                    controls.skipToPrevious()
                }
                "play_pause" -> {
                    val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                    if (isPlaying) {
                        appendLog("[MEDIA] Executando phone.media: pause (${controller.packageName})")
                        controls.pause()
                    } else {
                        appendLog("[MEDIA] Executando phone.media: play (${controller.packageName})")
                        controls.play()
                    }
                }
                "next" -> {
                    appendLog("[MEDIA] Executando phone.media: next (${controller.packageName})")
                    controls.skipToNext()
                }
                else -> {
                    appendLog("[MEDIA] phone.media ação desconhecida: $action")
                }
            }
            HyprLinkConnectionService.instance?.triggerPhoneStatusDebounce(500L)
        } catch (e: Exception) {
            appendLog("[WARN] Erro ao executar phone.media ($action): ${e.message}")
        }
    }

    private fun handlePhoneFind(context: Context, body: Map<String, Any?>?) {
        val request = com.example.find.PhoneFindRequest.fromMap(body)
        if (request == null) {
            appendLog("[ENCONTRAR] Pedido phone.find inválido (sem modos ativos ou dados inválidos)")
            return
        }
        com.example.find.PhoneFindManager.start(
            request = request,
            context = context,
            sendState = { state ->
                sendOneWayPacket("phone.find.state", state.toMap())
            },
            logger = { line ->
                appendLog(line)
            }
        )
    }

    private fun handlePhoneFindStop(context: Context) {
        com.example.find.PhoneFindManager.stop(
            stoppedBy = com.example.find.PhoneFindStopReason.PC,
            context = context
        )
    }

    private fun postRemoteOpenNotification(
        context: Context,
        title: String,
        text: String,
        intent: Intent
    ) {
        ensureRemoteOpenNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = (System.currentTimeMillis() % 100000).toInt() + 10000

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, notifId, intent, flags)

        val notif = NotificationCompat.Builder(context, REMOTE_OPEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(notifId, notif)
    }

    private fun postUnknownPackageNotification(context: Context, packageName: String) {
        ensureRemoteOpenNotificationChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = (System.currentTimeMillis() % 100000).toInt() + 20000
        val strings = LanguageManager.currentStrings
        val notif = NotificationCompat.Builder(context, REMOTE_OPEN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(strings.appNotFoundTitle)
            .setContentText(java.lang.String.format(strings.appNotFoundDesc, packageName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notif)
    }

    const val NOTIFICATION_WEBCAM_PROMPT_ID = 105

    fun isAppInForeground(): Boolean {
        return try {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        } catch (e: Exception) {
            false
        }
    }

    private fun postWebcamStartNotification(
        context: Context,
        packetId: Long,
        width: Int,
        height: Int,
        fps: Int,
        codec: String
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "hyprlink_webcam_prompt_channel"
        val channel = NotificationChannel(
            channelId,
            "HyprLink Camera Prompt",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification to prompt user to start camera"
        }
        nm.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_START_WEBCAM
            putExtra("packet_id", packetId)
            putExtra("width", width)
            putExtra("height", height)
            putExtra("fps", fps)
            putExtra("codec", codec)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_WEBCAM_PROMPT_ID, intent, flags)

        val strings = getAppStrings(LanguageManager.currentLanguage.value)
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(strings.tapToStartCamera)
            .setContentText(strings.tapToStartCameraDesc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_WEBCAM_PROMPT_ID, notif)
    }

    const val NOTIFICATION_MIC_PROMPT_ID = 107

    fun postMicStartNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "hyprlink_webcam_prompt_channel"
        val channel = NotificationChannel(
            channelId,
            "HyprLink Media Prompt",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification to prompt user to start camera or microphone"
        }
        nm.createNotificationChannel(channel)

        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_START_MIC
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_MIC_PROMPT_ID, intent, flags)

        val strings = getAppStrings(LanguageManager.currentLanguage.value)
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(strings.tapToStartMic)
            .setContentText(strings.tapToStartMicDesc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_MIC_PROMPT_ID, notif)
    }

    const val NOTIFICATION_DND_PROMPT_ID = 106

    private fun promptDndPermission(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (isAppInForeground()) {
            try {
                context.startActivity(intent)
                appendLog("[AUDIO] Abrindo definições de Não Incomodar diretamente (foreground)")
                return
            } catch (e: Exception) {
                appendLog("[WARN] Falha ao abrir definições de Não Incomodar: ${e.message}")
            }
        }

        // Em background, usar notificação de alta prioridade com PendingIntent
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "hyprlink_dnd_prompt_channel"
        val channel = NotificationChannel(
            channelId,
            "HyprLink Permissions Prompt",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications prompting for required system permissions"
        }
        nm.createNotificationChannel(channel)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_DND_PROMPT_ID, intent, flags)

        val strings = getAppStrings(LanguageManager.currentLanguage.value)
        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(strings.dndPermissionPromptTitle)
            .setContentText(strings.dndPermissionPromptDesc)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIFICATION_DND_PROMPT_ID, notif)
        appendLog("[AUDIO] Notificação de permissão Não Incomodar emitida (background)")
    }

    private fun postNotification(context: Context, title: String, body: String, appName: String) {
        val channelId = "workstation_notifications_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Workstation Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSubText(appName)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        appendLog("[NOTIF] Posted notification from workstation: $title")
    }

    // --- HELPERS FOR STREAM READING/WRITING WITH FRAMING ---

    private fun writeFramedBytes(outputStream: java.io.OutputStream, bytes: ByteArray) {
        val size = bytes.size
        val frameHeader = byteArrayOf(
            ((size shr 24) and 0xFF).toByte(),
            ((size shr 16) and 0xFF).toByte(),
            ((size shr 8) and 0xFF).toByte(),
            (size and 0xFF).toByte()
        )
        outputStream.write(frameHeader)
        outputStream.write(bytes)
        outputStream.flush()
    }

    private fun readFramedBytes(inputStream: java.io.InputStream): ByteArray {
        val header = readExact(inputStream, 4)
        val responseSize = ((header[0].toInt() and 0xFF) shl 24) or
                           ((header[1].toInt() and 0xFF) shl 16) or
                           ((header[2].toInt() and 0xFF) shl 8) or
                           (header[3].toInt() and 0xFF)
        
        if (responseSize <= 0 || responseSize > 10 * 1024 * 1024) {
            throw IOException("Invalid frame size: $responseSize")
        }
        return readExact(inputStream, responseSize)
    }

    private fun readExact(inputStream: java.io.InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var readBytes = 0
        while (readBytes < size) {
            val r = inputStream.read(buffer, readBytes, size - readBytes)
            if (r == -1) {
                throw IOException("End of stream reached")
            }
            readBytes += r
        }
        return buffer
    }

    // --- CBOR PACKET SERIALIZER / DESERIALIZER ---

    private fun encodeCborValue(value: Any?): DataItem {
        return when (value) {
            is String -> UnicodeString(value)
            is Float -> SinglePrecisionFloat(value)
            is Double -> DoublePrecisionFloat(value)
            is Number -> {
                val l = value.toLong()
                if (l < 0) NegativeInteger(l) else UnsignedInteger(l)
            }
            is Boolean -> if (value) SimpleValue.TRUE else SimpleValue.FALSE
            is ByteArray -> ByteString(value)
            is List<*> -> {
                val array = co.nstant.`in`.cbor.model.Array()
                array.setChunked(true)
                for (item in value) {
                    array.add(encodeCborValue(item))
                }
                array.add(Special.BREAK)
                array
            }
            is Map<*, *> -> {
                val map = co.nstant.`in`.cbor.model.Map()
                if (value.isNotEmpty()) {
                    map.setChunked(true)
                    for ((k, v) in value) {
                        val keyStr = k?.toString() ?: "null"
                        map.put(UnicodeString(keyStr), encodeCborValue(v))
                    }
                }
                map
            }
            null -> SimpleValue.NULL
            else -> UnicodeString(value.toString())
        }
    }

    private fun decodeCborValue(item: DataItem?): Any? {
        return when (item) {
            is UnicodeString -> item.string
            is UnsignedInteger -> item.value.toLong()
            is NegativeInteger -> item.value.toLong()
            is SinglePrecisionFloat -> item.value
            is HalfPrecisionFloat -> item.value
            is DoublePrecisionFloat -> item.value
            is SimpleValue -> {
                when (item) {
                    SimpleValue.TRUE -> true
                    SimpleValue.FALSE -> false
                    SimpleValue.NULL -> null
                    else -> item.toString()
                }
            }
            is ByteString -> item.bytes
            is co.nstant.`in`.cbor.model.Array -> {
                item.getDataItems()
                    .filter { it != Special.BREAK }
                    .map { decodeCborValue(it) }
            }
            is co.nstant.`in`.cbor.model.Map -> {
                val map = mutableMapOf<String, Any?>()
                for (keyItem in item.keys) {
                    if (keyItem is UnicodeString) {
                        map[keyItem.string] = decodeCborValue(item.get(keyItem))
                    }
                }
                map
            }
            else -> item?.toString()
        }
    }

    fun encodePacket(packet: Packet): ByteArray {
        val baos = ByteArrayOutputStream()
        val builder = CborBuilder()
        val mapBuilder = builder.addMap()
        
        mapBuilder.put(UnicodeString("id"), UnsignedInteger(packet.id))
        mapBuilder.put(UnicodeString("type"), UnicodeString(packet.type))
        
        if (packet.body != null) {
            val bodyMap = co.nstant.`in`.cbor.model.Map()
            if (packet.body.isNotEmpty()) {
                bodyMap.setChunked(true)
                for ((key, value) in packet.body) {
                    bodyMap.put(UnicodeString(key), encodeCborValue(value))
                }
            }
            mapBuilder.put(UnicodeString("body"), bodyMap)
        } else {
            mapBuilder.put(UnicodeString("body"), SimpleValue.NULL)
        }
        
        mapBuilder.put(UnicodeString("has_payload"), if (packet.hasPayload) SimpleValue.TRUE else SimpleValue.FALSE)
        mapBuilder.end()
        
        CborEncoder(baos).encode(builder.build())
        return baos.toByteArray()
    }

    fun decodePacket(bytes: ByteArray): Packet? {
        try {
            val bais = ByteArrayInputStream(bytes)
            val decoded = CborDecoder(bais).decode()
            if (decoded.isEmpty()) return null
            val mainItem = decoded[0] as? co.nstant.`in`.cbor.model.Map ?: return null
            
            val idItem = mainItem.get(UnicodeString("id"))
            val id = when (idItem) {
                is UnsignedInteger -> idItem.value.toLong()
                is NegativeInteger -> idItem.value.toLong()
                else -> 0L
            }
            
            val typeItem = mainItem.get(UnicodeString("type")) as? UnicodeString ?: return null
            val type = typeItem.string
            
            val bodyItem = mainItem.get(UnicodeString("body"))
            val body = if (bodyItem is co.nstant.`in`.cbor.model.Map) {
                @Suppress("UNCHECKED_CAST")
                decodeCborValue(bodyItem) as? Map<String, Any?>
            } else null
            
            val hasPayloadItem = mainItem.get(UnicodeString("has_payload"))
            val hasPayload = hasPayloadItem == SimpleValue.TRUE
            
            return Packet(id, type, body, hasPayload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
