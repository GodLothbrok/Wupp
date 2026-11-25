package com.wa.beastclient.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wa.beastclient.R
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.screens.WhatsAppAvatar
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocalWuppShowSeconds
import com.wa.beastclient.ui.theme.LocalWuppUse24Hour
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.max

/**
 * Interactive Voice Note (PTT) bubble with mirrored incoming/outgoing layout:
 * - Mirrored layout: outgoing places Avatar/Speed on the left; incoming places Play on the left & Avatar/Speed on the right
 * - Avatar smoothly morphs into the 1×/1.5×/2× speed pill during playback so the 40-bar waveform stays wide and uncluttered
 * - Tap & drag waveform scrubbing with active thumb dot + Ghost Audio receipt guard
 */
@Composable
fun VoiceNoteBubble(
    msg: MessageEntity,
    ghostPlayedActive: Boolean,
    onManualSendPlayedReceipt: () -> Unit,
    onRequestDownload: () -> Unit = {},
    modifier: Modifier = Modifier,
    avatarPath: String? = null,
    onClickAvatar: (() -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val context = LocalContext.current

    val peaks = remember(msg.waveformCsv) {
        val parsed = msg.waveformCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (parsed.size >= 40) parsed.take(40)
        else List(40) { idx -> parsed.getOrElse(idx) { 18 + ((idx * 17) % 64) } }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var autoPlayOnDownload by remember { mutableStateOf(false) }
    var playbackFraction by remember { mutableFloatStateOf(0f) }
    var currentSec by remember { mutableIntStateOf(0) }
    var speedIndex by remember { mutableIntStateOf(0) }
    val speeds = listOf(1.0f, 1.5f, 2.0f)

    val mediaFileExists = remember(msg.localMediaPath) {
        !msg.localMediaPath.isNullOrBlank() &&
            File(msg.localMediaPath).let { it.exists() && it.length() > 0L }
    }
    val exoPlayer = remember(msg.id, msg.localMediaPath, mediaFileExists) {
        if (mediaFileExists) {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
            ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttrs, true)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(msg.localMediaPath!!))))
                    prepare()
                }
        } else null
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    playbackFraction = 0f
                    currentSec = 0
                    exoPlayer?.seekTo(0L)
                    exoPlayer?.playWhenReady = false
                }
            }
        }
        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.release()
        }
    }

    LaunchedEffect(exoPlayer, autoPlayOnDownload) {
        if (exoPlayer != null) {
            isDownloading = false
            if (autoPlayOnDownload) {
                autoPlayOnDownload = false
                exoPlayer.playbackParameters = PlaybackParameters(speeds[speedIndex])
                exoPlayer.play()
                isPlaying = true
                if (!msg.isFromMe && msg.deliveryStatus != "PLAYED" && !ghostPlayedActive) {
                    onManualSendPlayedReceipt()
                }
            }
        }
    }

    LaunchedEffect(isDownloading, mediaFileExists) {
        if (isDownloading && !mediaFileExists) {
            delay(15_000L)
            isDownloading = false
            autoPlayOnDownload = false
        }
    }

    LaunchedEffect(isPlaying, isScrubbing, exoPlayer, speedIndex) {
        exoPlayer?.playbackParameters = PlaybackParameters(speeds[speedIndex])
        while (isPlaying && !isScrubbing && exoPlayer != null) {
            val dur = max(1L, exoPlayer.duration)
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            playbackFraction = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            currentSec = (pos / 1000L).toInt()
            delay(40L)
        }
    }

    val totalDurationSec = remember(msg.durationSec, exoPlayer?.duration) {
        val playerSec = exoPlayer?.duration?.takeIf { it > 0L }?.let { (it / 1000L).toInt() } ?: 0
        max(1, if (playerSec > 0) playerSec else msg.durationSec)
    }

    val showSeconds = LocalWuppShowSeconds.current
    val bubbleSurfaceColor = if (msg.isFromMe) colors.outgoingBubble else colors.incomingBubble
    val micTint = if (msg.deliveryStatus == "PLAYED") colors.blueTick else colors.brightGreen
    val showSpeedPill = isPlaying || isScrubbing || playbackFraction > 0.005f

    val avatarOrSpeedSlot: @Composable () -> Unit = {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = showSpeedPill,
                transitionSpec = {
                    (fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(150))) togetherWith
                        (fadeOut(tween(130)) + scaleOut(targetScale = 0.85f, animationSpec = tween(130)))
                },
                label = "ptt_avatar_speed_morph"
            ) { showingSpeed ->
                if (showingSpeed) {
                    val speedInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .expressivePressScale(speedInteraction, pressedScale = 0.90f)
                            .clip(CircleShape)
                            .background(if (colors.isDark) Color.Black.copy(alpha = 0.34f) else Color.Black.copy(alpha = 0.12f))
                            .border(0.6.dp, Color.White.copy(alpha = if (colors.isDark) 0.14f else 0.08f), CircleShape)
                            .height(27.dp)
                            .width(44.dp)
                            .clickable(
                                interactionSource = speedInteraction,
                                indication = null
                            ) {
                                speedIndex = (speedIndex + 1) % speeds.size
                                exoPlayer?.playbackParameters = PlaybackParameters(speeds[speedIndex])
                            }
                            .semantics { contentDescription = "Playback speed. Tap to change." },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (speedIndex) {
                                1 -> "1.5×"
                                2 -> "2×"
                                else -> "1×"
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = colors.textPrimary
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .then(
                                if (onClickAvatar != null) {
                                    Modifier.clickable(onClick = onClickAvatar)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val initials = msg.senderName.ifBlank { msg.senderJid.substringBefore("@") }
                            .trim()
                            .take(1)
                            .uppercase()
                            .ifBlank { null }
                        WhatsAppAvatar(
                            jid = if (msg.isFromMe) "" else msg.senderJid,
                            avatarPath = avatarPath,
                            isGroup = false,
                            size = 44.dp,
                            initialChar = initials
                        )
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .align(if (msg.isFromMe) Alignment.BottomEnd else Alignment.BottomStart)
                                .clip(CircleShape)
                                .background(bubbleSurfaceColor)
                                .padding(1.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.msg_status_mic),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(micTint),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    val playButtonSlot: @Composable () -> Unit = {
        val playInteraction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(36.dp)
                .expressivePressScale(playInteraction, pressedScale = 0.86f)
                .clip(CircleShape)
                .clickable(
                    interactionSource = playInteraction,
                    indication = null
                ) {
                    if (exoPlayer != null) {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
                            }
                            exoPlayer.playbackParameters = PlaybackParameters(speeds[speedIndex])
                            exoPlayer.play()
                            if (!msg.isFromMe && msg.deliveryStatus != "PLAYED" && !ghostPlayedActive) {
                                onManualSendPlayedReceipt()
                            }
                        }
                    } else {
                        isDownloading = true
                        autoPlayOnDownload = true
                        onRequestDownload()
                    }
                }
                .semantics {
                    contentDescription = when {
                        isDownloading -> "Downloading voice message"
                        isPlaying -> "Pause voice message"
                        exoPlayer == null -> "Download voice message"
                        else -> "Play voice message"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isDownloading && exoPlayer == null) {
                @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                LoadingIndicator(
                    modifier = Modifier.size(22.dp),
                    color = colors.textPrimary
                )
            } else {
                Icon(
                    imageVector = when {
                        isPlaying -> Icons.Rounded.Pause
                        exoPlayer == null -> Icons.Rounded.Download
                        else -> Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.95f),
                    modifier = Modifier.size(if (exoPlayer == null) 22.dp else 30.dp)
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (msg.isFromMe) {
                avatarOrSpeedSlot()
                Spacer(modifier = Modifier.width(6.dp))
                playButtonSlot()
                Spacer(modifier = Modifier.width(6.dp))
            } else {
                playButtonSlot()
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Wide 40-Bar Interactive Preview Waveform + Scrubber Thumb Dot + Duration/Meta Row
            Column(modifier = Modifier.weight(1f)) {
                val activeBarColor = if (msg.deliveryStatus == "PLAYED" && !msg.isFromMe) {
                    colors.blueTick
                } else if (msg.isFromMe) {
                    Color.White.copy(alpha = 0.92f)
                } else {
                    colors.brightGreen
                }
                val inactiveBarColor = if (msg.isFromMe) {
                    Color.White.copy(alpha = 0.34f)
                } else {
                    colors.textSecondary.copy(alpha = if (colors.isDark) 0.40f else 0.34f)
                }
                val thumbColor = if (msg.deliveryStatus == "PLAYED" && !msg.isFromMe) {
                    colors.blueTick
                } else if (msg.isFromMe) {
                    colors.blueTick
                } else {
                    colors.brightGreen
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .semantics {
                            contentDescription = "Voice message playback position"
                            progressBarRangeInfo = ProgressBarRangeInfo(playbackFraction, 0f..1f)
                            setProgress { target ->
                                val player = exoPlayer
                                if (player != null && player.duration > 0L) {
                                    val fraction = target.coerceIn(0f, 1f)
                                    player.seekTo((player.duration * fraction).toLong())
                                    playbackFraction = fraction
                                    currentSec = ((player.duration * fraction) / 1000f).toInt()
                                    true
                                } else false
                            }
                        }
                        .pointerInput(exoPlayer, peaks, totalDurationSec) {
                            detectTapGestures { offset ->
                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                playbackFraction = frac
                                currentSec = (totalDurationSec * frac).toInt()
                                if (exoPlayer != null && exoPlayer.duration > 0) {
                                    exoPlayer.seekTo((exoPlayer.duration * frac).toLong())
                                }
                            }
                        }
                        .pointerInput(exoPlayer, peaks, totalDurationSec) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isScrubbing = true
                                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    playbackFraction = frac
                                    currentSec = (totalDurationSec * frac).toInt()
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    playbackFraction = frac
                                    currentSec = (totalDurationSec * frac).toInt()
                                },
                                onDragEnd = {
                                    if (exoPlayer != null && exoPlayer.duration > 0) {
                                        exoPlayer.seekTo((exoPlayer.duration * playbackFraction).toLong())
                                    }
                                    isScrubbing = false
                                },
                                onDragCancel = {
                                    isScrubbing = false
                                }
                            )
                        }
                ) {
                    val thumbRadius = (if (isScrubbing) 6.dp else 4.8.dp).toPx()
                    val padH = thumbRadius
                    val trackW = (size.width - padH * 2f).coerceAtLeast(1f)
                    val barCount = peaks.size
                    val slotW = trackW / barCount
                    val barW = (slotW * 0.56f).coerceIn(2.dp.toPx(), 3.2.dp.toPx())
                    val maxH = size.height * 0.80f
                    val centerY = size.height / 2f

                    peaks.forEachIndexed { idx, amp ->
                        val ratio = (idx + 0.5f) / barCount.toFloat()
                        val barH = ((amp.coerceIn(12, 99) / 99f) * maxH).coerceAtLeast(3.dp.toPx())
                        val x = padH + idx * slotW + (slotW - barW) / 2f
                        val active = ratio <= playbackFraction && (playbackFraction > 0f || isPlaying || isScrubbing)
                        drawRoundRect(
                            color = if (active) activeBarColor else inactiveBarColor,
                            topLeft = Offset(x, centerY - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }

                    // Active Scrubber Thumb Dot riding along the waveform
                    val thumbX = (padH + trackW * playbackFraction).coerceIn(padH, size.width - padH)
                    drawCircle(
                        color = bubbleSurfaceColor,
                        radius = thumbRadius + 1.6.dp.toPx(),
                        center = Offset(thumbX, centerY)
                    )
                    drawCircle(
                        color = thumbColor,
                        radius = thumbRadius,
                        center = Offset(thumbX, centerY)
                    )
                }

                Spacer(modifier = Modifier.height(1.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val shownSec = if (isPlaying || isScrubbing || currentSec > 0) currentSec else totalDurationSec
                    val durationText = "%d:%02d".format(shownSec / 60, shownSec % 60)
                    Text(
                        text = durationText,
                        fontSize = 11.5.sp,
                        fontFamily = mono,
                        color = if (isScrubbing) colors.brightGreen else colors.textSecondary,
                        fontWeight = if (isScrubbing) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    MessageMetaCluster(
                        timeText = formatMessageClock(
                            msg.timestamp,
                            includeSeconds = showSeconds,
                            use24Hour = LocalWuppUse24Hour.current
                        ),
                        isEdited = msg.isEdited,
                        isEphemeral = msg.isEphemeral,
                        isFromMe = msg.isFromMe,
                        deliveryStatus = msg.deliveryStatus
                    )
                }
            }

            if (!msg.isFromMe) {
                Spacer(modifier = Modifier.width(8.dp))
                avatarOrSpeedSlot()
            }
        }
    }
}
