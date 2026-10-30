// SPDX-License-Identifier: GPL-3.0-or-later
package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PcPlayerNotificationManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        const val CHANNEL_ID = "pc_player"
        const val NOTIFICATION_ID = 20005
        const val PREF_SHOW_PC_PLAYER = "show_pc_player_notification"
        private const val TAG = "PC"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var mediaSession: MediaSessionCompat? = null

    // Cache
    private var cachedArtId: String? = null
    private var cachedBitmap: Bitmap? = null

    // Optimistic state
    private var optimisticStatus: String? = null
    private var lastKnownPcStatus: String? = null

    private var observerJob: Job? = null

    init {
        createChannel()
        initMediaSession()
        startObserving()
    }

    private fun createChannel() {
        val strings = LanguageManager.currentStrings
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.channelPcPlayerName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = strings.channelPcPlayerDesc
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(context, TAG).apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    onPlayPauseClicked(forcePlay = true)
                }

                override fun onPause() {
                    onPlayPauseClicked(forcePause = true)
                }

                override fun onSkipToPrevious() {
                    onPreviousClicked()
                }

                override fun onSkipToNext() {
                    onNextClicked()
                }

                override fun onSeekTo(pos: Long) {
                    // Daemon MPRIS currently does not support seek via PROTOCOL.md.
                    // Kept read-only as documented in LOTE-NOTAS.md.
                }
            })
        }
    }

    private fun startObserving() {
        observerJob = scope.launch {
            launch {
                ConnectionRepository.mediaState.collectLatest { state ->
                    handleIncomingMediaState(state)
                }
            }
            launch {
                ConnectionRepository.connectionStatus.collectLatest { status ->
                    if (status != ConnectionStatus.CONNECTED) {
                        dismiss()
                    }
                }
            }
        }
    }

    private suspend fun handleIncomingMediaState(state: MediaState?) {
        val currentPcStatus = state?.status
        if (currentPcStatus != lastKnownPcStatus) {
            // PC state changed; PC wins, clear optimistic state
            optimisticStatus = null
            lastKnownPcStatus = currentPcStatus
        }

        // Handle artwork decoding / caching
        val action = PcPlayerLogic.resolveArtworkAction(
            incomingBytes = state?.artBytes,
            incomingArtId = state?.artId,
            cachedArtId = cachedArtId,
            hasCachedBitmap = cachedBitmap != null
        )

        when (action) {
            is ArtworkResolution.DecodeNew -> {
                val decoded = withContext(Dispatchers.IO) {
                    try {
                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        BitmapFactory.decodeByteArray(action.bytes, 0, action.bytes.size, options)
                    } catch (_: Throwable) {
                        null
                    }
                }
                cachedBitmap = decoded
                cachedArtId = action.artId
                ConnectionRepository.currentArtworkBitmap.value = decoded
            }
            is ArtworkResolution.ReuseCache -> {
                // Keep cachedBitmap
            }
            is ArtworkResolution.ClearCache -> {
                cachedBitmap = null
                cachedArtId = null
                ConnectionRepository.currentArtworkBitmap.value = null
            }
        }

        updateNotificationAndSession(state)
    }

    fun onPlayPauseClicked(forcePlay: Boolean = false, forcePause: Boolean = false) {
        val state = ConnectionRepository.mediaState.value
        val effective = PcPlayerLogic.resolveEffectiveStatus(state?.status, optimisticStatus)
        val newStatus = when {
            forcePlay -> "Playing"
            forcePause -> "Paused"
            else -> PcPlayerLogic.togglePlayPause(effective)
        }
        optimisticStatus = newStatus

        // Immediately update notification & session optimistically
        updateNotificationAndSession(state)

        // Dispatch remote command
        val cmd = if (newStatus == "Playing") "play" else "pause"
        scope.launch {
            try {
                ConnectionRepository.sendMediaCommand(cmd)
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Media command $cmd failed: ${e.message}")
            }
        }
    }

    fun onPreviousClicked() {
        scope.launch {
            try {
                ConnectionRepository.sendMediaCommand("previous")
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Media command previous failed: ${e.message}")
            }
        }
    }

    fun onNextClicked() {
        scope.launch {
            try {
                ConnectionRepository.sendMediaCommand("next")
            } catch (e: Exception) {
                ConnectionRepository.appendLog("[WARN] Media command next failed: ${e.message}")
            }
        }
    }

    fun onSettingChanged(enabled: Boolean) {
        if (!enabled) {
            dismiss()
        } else {
            val state = ConnectionRepository.mediaState.value
            updateNotificationAndSession(state)
        }
    }

    private fun isSettingEnabled(): Boolean {
        val prefs = context.getSharedPreferences("hyprlink_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_SHOW_PC_PLAYER, true)
    }

    private fun updateNotificationAndSession(state: MediaState?) {
        val isConnected = ConnectionRepository.connectionStatus.value == ConnectionStatus.CONNECTED
        val isSettingOn = isSettingEnabled()
        val hasTrack = !state?.title.isNullOrBlank()
        val effectiveStatus = PcPlayerLogic.resolveEffectiveStatus(state?.status, optimisticStatus)

        val shouldShow = PcPlayerLogic.shouldShowNotification(
            status = effectiveStatus,
            isConnected = isConnected,
            isSettingEnabled = isSettingOn,
            hasTitle = hasTrack
        )

        if (!shouldShow) {
            dismiss()
            return
        }

        val session = mediaSession ?: return
        val strings = LanguageManager.currentStrings
        val isPlaying = PcPlayerLogic.isPlaying(effectiveStatus)

        // 1. Update PlaybackStateCompat
        val positionMs = state?.positionMs ?: 0L
        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                positionMs,
                if (isPlaying) 1.0f else 0.0f,
                SystemClock.elapsedRealtime()
            )
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )

        session.setPlaybackState(playbackStateBuilder.build())

        // 2. Update MediaMetadataCompat
        val durationMs = state?.lengthMs ?: -1L
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, state?.title ?: strings.untitled)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state?.artist ?: strings.unknownArtist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)

        state?.album?.let { album ->
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
        }

        cachedBitmap?.let { bmp ->
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bmp)
        }

        session.setMetadata(metadataBuilder.build())
        if (!session.isActive) {
            session.isActive = true
        }

        // 3. Post / Update System Notification
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val prevIntent = Intent(context, HyprLinkConnectionService::class.java).apply {
            action = HyprLinkConnectionService.ACTION_MEDIA_PREV
        }
        val prevPending = PendingIntent.getService(context, 101, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val playPauseIntent = Intent(context, HyprLinkConnectionService::class.java).apply {
            action = HyprLinkConnectionService.ACTION_MEDIA_PLAY_PAUSE
        }
        val playPausePending = PendingIntent.getService(context, 102, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val nextIntent = Intent(context, HyprLinkConnectionService::class.java).apply {
            action = HyprLinkConnectionService.ACTION_MEDIA_NEXT
        }
        val nextPending = PendingIntent.getService(context, 103, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(context, 100, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_hyprlink)
            .setContentTitle(state?.title ?: strings.untitled)
            .setContentText(state?.artist ?: strings.unknownArtist)
            .setSubText(strings.playingOnPc)
            .setContentIntent(contentPending)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(mediaStyle)
            .addAction(R.drawable.ic_media_prev, strings.btnPrevious, prevPending)
            .addAction(
                if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                if (isPlaying) strings.btnPause else strings.btnPlay,
                playPausePending
            )
            .addAction(R.drawable.ic_media_next, strings.btnNext, nextPending)

        cachedBitmap?.let { bmp ->
            notifBuilder.setLargeIcon(bmp)
        }

        notificationManager.notify(NOTIFICATION_ID, notifBuilder.build())
    }

    fun dismiss() {
        notificationManager.cancel(NOTIFICATION_ID)
        mediaSession?.let {
            if (it.isActive) {
                it.isActive = false
            }
        }
        optimisticStatus = null
        ConnectionRepository.currentArtworkBitmap.value = null
    }

    fun destroy() {
        observerJob?.cancel()
        dismiss()
        mediaSession?.release()
        mediaSession = null
        cachedBitmap = null
        cachedArtId = null
    }
}
