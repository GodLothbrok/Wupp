package com.wa.beastclient.data.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wa.beastclient.MainActivity
import com.wa.beastclient.R
import com.wa.beastclient.core.service.NotificationActionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

data class VoiceNotePlaybackState(
    val activeMessageId: String? = null,
    val chatJid: String? = null,
    val senderLabel: String = "",
    val filePath: String? = null,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speedIndex: Int = 0
)

/**
 * Application-scoped voice note playback controller so audio survives LazyColumn item disposal
 * while scrolling, screen navigation, and app backgrounding.
 */
object VoiceNotePlaybackManager {
    private const val VOICE_CHANNEL_ID = "wa_voice_playback_channel"
    private const val VOICE_NOTIF_ID = 1049
    internal val PLAYBACK_SPEEDS = listOf(1.0f, 1.5f, 2.0f)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(VoiceNotePlaybackState())
    val state: StateFlow<VoiceNotePlaybackState> = _state.asStateFlow()

    private var appContext: Context? = null
    private var exoPlayer: ExoPlayer? = null
    private var tickerJob: Job? = null
    private var channelCreated = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = exoPlayer ?: return
            val dur = resolveDurationMs(player)
            val pos = player.currentPosition.coerceAtLeast(0L)
            _state.value = _state.value.copy(
                isPlaying = isPlaying,
                isEnded = if (isPlaying) false else _state.value.isEnded,
                currentPositionMs = pos,
                durationMs = dur
            )
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
            }
            appContext?.let { updatePlaybackNotification(it) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            if (playbackState == Player.STATE_READY) {
                val dur = resolveDurationMs(player)
                _state.value = _state.value.copy(durationMs = dur)
            } else if (playbackState == Player.STATE_ENDED) {
                stopTicker()
                player.playWhenReady = false
                player.seekTo(0L)
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isEnded = true,
                    currentPositionMs = 0L,
                    durationMs = resolveDurationMs(player)
                )
                appContext?.let { cancelPlaybackNotification(it) }
            }
        }
    }

    private fun ensurePlayer(context: Context): ExoPlayer {
        val ctx = context.applicationContext
        appContext = ctx
        exoPlayer?.let { return it }
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        return ExoPlayer.Builder(ctx)
            .setAudioAttributes(audioAttrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { player ->
                player.addListener(playerListener)
                exoPlayer = player
            }
    }

    private fun resolveDurationMs(player: ExoPlayer): Long {
        val playerDur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        return max(_state.value.durationMs, playerDur)
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (true) {
                val player = exoPlayer ?: break
                if (!player.isPlaying) break
                val dur = resolveDurationMs(player)
                val pos = player.currentPosition.coerceAtLeast(0L)
                _state.value = _state.value.copy(
                    currentPositionMs = pos,
                    durationMs = dur
                )
                delay(40L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /**
     * Toggles play/pause for [messageId], or switches playback to [filePath] if another note was active.
     * Returns true when playback started/resumed, false when paused or file missing.
     */
    fun togglePlayPause(
        context: Context,
        messageId: String,
        chatJid: String,
        senderLabel: String,
        filePath: String,
        durationHintSec: Int = 0,
        initialSeekFraction: Float = 0f,
        onStartedPlaying: (() -> Unit)? = null
    ) {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) return
        val ctx = context.applicationContext
        scope.launch {
            val player = ensurePlayer(ctx)
            val current = _state.value
            if (current.activeMessageId == messageId && current.filePath == filePath) {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.playbackParameters = PlaybackParameters(speedForIndex(current.speedIndex))
                    player.play()
                    onStartedPlaying?.invoke()
                }
            } else {
                startNewTrack(
                    ctx = ctx,
                    player = player,
                    messageId = messageId,
                    chatJid = chatJid,
                    senderLabel = senderLabel,
                    file = file,
                    durationHintSec = durationHintSec,
                    initialSeekFraction = initialSeekFraction
                )
                onStartedPlaying?.invoke()
            }
        }
    }

    fun play(
        context: Context,
        messageId: String,
        chatJid: String,
        senderLabel: String,
        filePath: String,
        durationHintSec: Int = 0,
        initialSeekFraction: Float = 0f
    ) {
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) return
        val ctx = context.applicationContext
        scope.launch {
            val player = ensurePlayer(ctx)
            val current = _state.value
            if (current.activeMessageId == messageId && current.filePath == filePath) {
                if (!player.isPlaying) {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.playbackParameters = PlaybackParameters(speedForIndex(current.speedIndex))
                    player.play()
                }
            } else {
                startNewTrack(
                    ctx = ctx,
                    player = player,
                    messageId = messageId,
                    chatJid = chatJid,
                    senderLabel = senderLabel,
                    file = file,
                    durationHintSec = durationHintSec,
                    initialSeekFraction = initialSeekFraction
                )
            }
        }
    }

    private fun startNewTrack(
        ctx: Context,
        player: ExoPlayer,
        messageId: String,
        chatJid: String,
        senderLabel: String,
        file: File,
        durationHintSec: Int,
        initialSeekFraction: Float
    ) {
        val hintMs = durationHintSec.coerceAtLeast(0) * 1000L
        val speedIdx = _state.value.speedIndex
        val startPosMs = computeSeekPositionMs(initialSeekFraction, hintMs)
        _state.value = VoiceNotePlaybackState(
            activeMessageId = messageId,
            chatJid = chatJid,
            senderLabel = senderLabel,
            filePath = file.absolutePath,
            isPlaying = true,
            isEnded = false,
            currentPositionMs = startPosMs,
            durationMs = hintMs,
            speedIndex = speedIdx
        )
        player.stop()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.playbackParameters = PlaybackParameters(speedForIndex(speedIdx))
        player.prepare()
        if (startPosMs > 0L) {
            player.seekTo(startPosMs)
        }
        player.play()
        updatePlaybackNotification(ctx)
    }

    fun seekToFraction(messageId: String, fraction: Float) {
        scope.launch {
            val player = exoPlayer ?: return@launch
            if (_state.value.activeMessageId != messageId) return@launch
            val dur = resolveDurationMs(player)
            if (dur <= 0L) return@launch
            val targetMs = computeSeekPositionMs(fraction, dur)
            player.seekTo(targetMs)
            _state.value = _state.value.copy(
                currentPositionMs = targetMs,
                durationMs = dur,
                isEnded = false
            )
        }
    }

    fun cycleSpeed() {
        scope.launch {
            val nextIdx = nextSpeedIndex(_state.value.speedIndex)
            _state.value = _state.value.copy(speedIndex = nextIdx)
            exoPlayer?.playbackParameters = PlaybackParameters(speedForIndex(nextIdx))
            appContext?.let {
                if (_state.value.activeMessageId != null) {
                    updatePlaybackNotification(it)
                }
            }
        }
    }

    fun toggleCurrentPlayPause(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            val player = exoPlayer ?: return@launch
            val current = _state.value
            if (current.activeMessageId == null) return@launch
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0L)
                }
                player.playbackParameters = PlaybackParameters(speedForIndex(current.speedIndex))
                player.play()
            }
        }
    }

    fun stop(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        scope.launch {
            stopTicker()
            exoPlayer?.stop()
            _state.value = VoiceNotePlaybackState(speedIndex = _state.value.speedIndex)
            cancelPlaybackNotification(ctx)
        }
    }

    private fun ensureChannel(context: Context) {
        if (channelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val ch = NotificationChannel(
            VOICE_CHANNEL_ID,
            "Voice note playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Controls for voice note background playback"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
        channelCreated = true
    }

    private fun updatePlaybackNotification(context: Context) {
        val current = _state.value
        if (current.activeMessageId == null || current.isEnded) {
            cancelPlaybackNotification(context)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            ensureChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPi = PendingIntent.getActivity(
                context,
                VOICE_NOTIF_ID,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val toggleIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_VOICE_TOGGLE_PAUSE
            }
            val togglePi = PendingIntent.getBroadcast(
                context,
                VOICE_NOTIF_ID + 1,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_VOICE_STOP
            }
            val stopPi = PendingIntent.getBroadcast(
                context,
                VOICE_NOTIF_ID + 2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val speedLabel = when (current.speedIndex) {
                1 -> "1.5×"
                2 -> "2×"
                else -> "1×"
            }
            val shownSec = (current.currentPositionMs / 1000L).toInt().coerceAtLeast(0)
            val totalSec = (current.durationMs / 1000L).toInt().coerceAtLeast(shownSec)
            val timeText = "%d:%02d / %d:%02d".format(
                shownSec / 60, shownSec % 60,
                totalSec / 60, totalSec % 60
            )
            val statusLine = if (current.isPlaying) {
                "Playing ($speedLabel) · $timeText"
            } else {
                "Paused · $timeText"
            }
            val title = if (current.senderLabel.isNotBlank()) {
                "Voice note · ${current.senderLabel}"
            } else {
                "Voice note"
            }

            val notif = NotificationCompat.Builder(context, VOICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_wupp)
                .setContentTitle(title)
                .setContentText(statusLine)
                .setContentIntent(openAppPi)
                .setOngoing(current.isPlaying)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    0,
                    if (current.isPlaying) "Pause" else "Play",
                    togglePi
                )
                .addAction(0, "Stop", stopPi)
                .build()
            nm.notify(VOICE_NOTIF_ID, notif)
        }
    }

    private fun cancelPlaybackNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(VOICE_NOTIF_ID)
        }
    }

    internal fun nextSpeedIndex(currentIndex: Int): Int =
        (currentIndex + 1) % PLAYBACK_SPEEDS.size

    internal fun speedForIndex(index: Int): Float =
        PLAYBACK_SPEEDS.getOrElse(index) { 1.0f }

    internal fun computePlaybackFraction(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.coerceAtLeast(0L).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    internal fun computeSeekPositionMs(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return (durationMs * fraction.coerceIn(0f, 1f)).toLong().coerceIn(0L, durationMs)
    }
}
