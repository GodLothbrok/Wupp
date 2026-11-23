package com.wa.beastclient.ui.calling

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.wa.beastclient.core.calling.CallAudioRoute
import com.wa.beastclient.core.calling.CallPhase
import com.wa.beastclient.core.calling.CallStateManager
import com.wa.beastclient.core.calling.CallUiState
import com.wa.beastclient.core.calling.formatCallTimer
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.screens.WhatsAppAvatar
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import com.wa.beastclient.ui.theme.LocaleHelper
import com.wa.beastclient.ui.theme.WhatsAppTheme

/**
 * Full-screen native Voice & Video call interface backed by the hidden System WebView engine.
 * Supports lockscreen wake-up, minimize-to-banner for voice calls, and Android PiP for video calls.
 */
class CallActivity : ComponentActivity() {

    private var inPipModeState by mutableStateOf(false)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CallStateManager.init(this)

        setContent {
            val stealth by StealthMatrixManager.config.collectAsState()
            val callState by CallStateManager.uiState.collectAsState()
            var pendingAnswerAudioOnly by rememberSaveable(callState.callId) { mutableStateOf<Boolean?>(null) }
            var pendingUpgradeToVideo by rememberSaveable(callState.callId) { mutableStateOf(false) }

            val callPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                val camGranted = grants[Manifest.permission.CAMERA] == true ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val audioOnly = pendingAnswerAudioOnly
                val wantUpgrade = pendingUpgradeToVideo
                pendingAnswerAudioOnly = null
                pendingUpgradeToVideo = false
                if (micGranted && audioOnly != null) {
                    CallStateManager.answerIncomingCall(audioOnly = audioOnly || !camGranted)
                } else if (camGranted && wantUpgrade) {
                    CallStateManager.upgradeToVideo()
                }
                if (micGranted || camGranted) {
                    CallStateManager.onMediaPermissionsGranted()
                }
            }

            fun requestPermsAndAnswer(audioOnly: Boolean, needsCamera: Boolean) {
                val needed = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.RECORD_AUDIO)
                }
                if (needsCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.CAMERA)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                ) {
                    needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                if (needed.isEmpty()) {
                    CallStateManager.answerIncomingCall(audioOnly)
                } else {
                    pendingAnswerAudioOnly = audioOnly
                    pendingUpgradeToVideo = false
                    callPermissionLauncher.launch(needed.toTypedArray())
                }
            }

            fun requestCameraThenUpgrade() {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    CallStateManager.upgradeToVideo()
                } else {
                    pendingAnswerAudioOnly = null
                    pendingUpgradeToVideo = true
                    callPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                }
            }

            LaunchedEffect(callState.phase, callState.isVideo) {
                if (callState.phase == CallPhase.IDLE) {
                    finish()
                    return@LaunchedEffect
                }
                if (callState.phase == CallPhase.DIALING ||
                    callState.phase == CallPhase.CONNECTING ||
                    callState.phase == CallPhase.ACTIVE
                ) {
                    val needed = mutableListOf<String>()
                    if (ContextCompat.checkSelfPermission(this@CallActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        needed.add(Manifest.permission.RECORD_AUDIO)
                    }
                    if (callState.isVideo && !callState.isCameraOff &&
                        ContextCompat.checkSelfPermission(this@CallActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                    ) {
                        needed.add(Manifest.permission.CAMERA)
                    }
                    if (needed.isNotEmpty()) {
                        pendingAnswerAudioOnly = null
                        pendingUpgradeToVideo = false
                        callPermissionLauncher.launch(needed.toTypedArray())
                    }
                }
            }

            BackHandler {
                when (callState.phase) {
                    CallPhase.FAILED -> CallStateManager.dismissFailedScreen()
                    CallPhase.INCOMING_RINGING -> finish()
                    else -> finish()
                }
            }

            WhatsAppTheme(
                themeMode = stealth.themeMode,
                accentColor = stealth.accentColor,
                pureBlackOled = stealth.pureBlackOled,
                dynamicColors = stealth.dynamicMaterialYou,
                useSystemFont = stealth.useSystemFont,
                fontStyle = stealth.fontStyle,
                customFontPath = stealth.customFontPath,
                bubbleCornerRadiusDp = stealth.bubbleCornerRadiusDp,
                customOutgoingBubbleArgb = stealth.customOutgoingBubbleArgb,
                customIncomingBubbleArgb = stealth.customIncomingBubbleArgb,
                showSecondsInTimestamps = stealth.showSecondsInTimestamps,
                use24HourFormat = StealthMatrixManager.is24HourEnabled(this, stealth.clockFormat),
                chatFontScale = stealth.chatFontScale,
                classicUi = stealth.classicWhatsAppLook,
                assetManager = assets
            ) {
                CallScreenContent(
                    state = callState,
                    isInPip = inPipModeState,
                    onMinimize = { finish() },
                    onAnswer = { audioOnly -> requestPermsAndAnswer(audioOnly, callState.isVideo && !audioOnly) },
                    onDeclineOrEnd = { CallStateManager.declineOrEndCall() },
                    onToggleMute = { CallStateManager.toggleMute() },
                    onToggleRecording = { CallStateManager.toggleCallRecording() },
                    onToggleCamera = {
                        if (!callState.isVideo || callState.isCameraOff) {
                            requestCameraThenUpgrade()
                        } else {
                            CallStateManager.toggleCamera()
                        }
                    },
                    onFlipCamera = { CallStateManager.flipCamera() },
                    onSelectAudioRoute = { route -> CallStateManager.setAudioRoute(route) },
                    onOpenOfficialWa = { CallStateManager.openOfficialWhatsAppFallback() },
                    onDismissFailed = { CallStateManager.dismissFailedScreen() },
                    onAcceptVideoUpgrade = { requestCameraThenUpgrade() },
                    onDeclineVideoUpgrade = { CallStateManager.declineVideoUpgrade() }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = CallStateManager.uiState.value
        if (state.phase == CallPhase.ACTIVE && state.isVideo) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipModeState = isInPictureInPictureMode
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallScreenContent(
    state: CallUiState,
    isInPip: Boolean,
    onMinimize: () -> Unit,
    onAnswer: (audioOnly: Boolean) -> Unit,
    onDeclineOrEnd: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleRecording: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onSelectAudioRoute: (CallAudioRoute) -> Unit,
    onOpenOfficialWa: () -> Unit,
    onDismissFailed: () -> Unit,
    onAcceptVideoUpgrade: () -> Unit,
    onDeclineVideoUpgrade: () -> Unit
) {
    val mono = LocalWaMonoFamily.current
    val showVideoFeed = state.isVideo && (
        state.phase == CallPhase.ACTIVE ||
            state.phase == CallPhase.CONNECTING ||
            state.phase == CallPhase.DIALING
        ) && (!state.isCameraOff || state.hasRemoteVideo)
    var showChrome by remember { mutableStateOf(true) }
    var showRouteMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            CallStateManager.attachRemoteVideoSurface(null)
            CallStateManager.attachLocalVideoSurface(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A1418),
                        Color(0xFF0F2127),
                        Color(0xFF081014)
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (showVideoFeed && !isInPip) {
                    showChrome = !showChrome
                }
            }
    ) {
        if (showVideoFeed) {
            val remoteCwDeg by rememberUpdatedState(state.remoteOrientationDegrees)
            val remoteW by rememberUpdatedState(state.remoteVideoWidth)
            val remoteH by rememberUpdatedState(state.remoteVideoHeight)
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
                        var boundSurface: Surface? = null
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                applyVideoTextureTransform(this@apply, w, h, remoteW, remoteH, remoteCwDeg, fillCrop = false)
                                val s = Surface(st)
                                boundSurface = s
                                CallStateManager.attachRemoteVideoSurface(s)
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                applyVideoTextureTransform(this@apply, w, h, remoteW, remoteH, remoteCwDeg, fillCrop = false)
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                CallStateManager.attachRemoteVideoSurface(null)
                                boundSurface?.release()
                                boundSurface = null
                                return true
                            }
                        }
                    }
                },
                update = { tv ->
                    if (tv.width > 0 && tv.height > 0) {
                        applyVideoTextureTransform(tv, tv.width, tv.height, remoteW, remoteH, remoteCwDeg, fillCrop = false)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!showVideoFeed || !state.hasRemoteVideo) {
            VoiceCallBackdropContent(state = state, isInPip = isInPip)
        }

        if (showVideoFeed && !state.isCameraOff && !isInPip) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 68.dp, end = 16.dp)
                    .size(width = 104.dp, height = 148.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF101C22))
                    .border(1.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            var boundSurface: Surface? = null
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                    st.setDefaultBufferSize(640, 480)
                                    applyLocalPreviewTransform(this@apply, w, h)
                                    val s = Surface(st)
                                    boundSurface = s
                                    CallStateManager.attachLocalVideoSurface(s)
                                }
                                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                    applyLocalPreviewTransform(this@apply, w, h)
                                }
                                override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                    CallStateManager.attachLocalVideoSurface(null)
                                    boundSurface?.release()
                                    boundSurface = null
                                    return true
                                }
                            }
                        }
                    },
                    update = { tv ->
                        if (tv.width > 0 && tv.height > 0) {
                            applyLocalPreviewTransform(tv, tv.width, tv.height)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!isInPip) {
            AnimatedVisibility(
                visible = !showVideoFeed || showChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Top header bar
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x88142026))
                                .clickable(onClick = onMinimize)
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Minimize",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chats",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (state.isVideo) "End-to-end encrypted video" else "End-to-end encrypted call",
                                color = Color(0xCCDAE5EA),
                                fontSize = 11.5.sp
                            )
                            if (state.isRecording) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xCCE53935))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FiberManualRecord,
                                        contentDescription = "Recording",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "REC ${formatCallTimer(state.recordingDurationSec)}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = mono,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (showVideoFeed) {
                                Text(
                                    text = "${state.peerName} · ${state.statusText}",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = mono,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Bottom controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.pendingVideoUpgrade && state.phase == CallPhase.ACTIVE) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xE61A252C))
                                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(18.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Peer requested video",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = onDeclineVideoUpgrade) {
                                        Text("Voice", color = Color.White, fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = onAcceptVideoUpgrade,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                    ) {
                                        Text("Switch", color = Color.Black, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        when (state.phase) {
                            CallPhase.FAILED -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(Color(0xD91A252C))
                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = state.errorReason ?: "Couldn't connect call",
                                        color = Color(0xFFFF8A80),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        OutlinedButton(onClick = onDismissFailed) {
                                            Text("Close", color = Color.White)
                                        }
                                        Button(
                                            onClick = onOpenOfficialWa,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
                                        ) {
                                            Text("Open in WhatsApp", color = Color(0xFF0A1418))
                                        }
                                    }
                                }
                            }

                            CallPhase.INCOMING_RINGING -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(36.dp))
                                            .background(Color(0xD9162229))
                                            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(36.dp))
                                            .padding(horizontal = 28.dp, vertical = 18.dp)
                                    ) {
                                        CallRoundAction(
                                            icon = Icons.Rounded.CallEnd,
                                            label = "Decline",
                                            containerColor = Color(0xFFE53935),
                                            contentColor = Color.White,
                                            onClick = onDeclineOrEnd
                                        )
                                        if (state.canAnswerInApp) {
                                            CallRoundAction(
                                                icon = if (state.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call,
                                                label = "Answer",
                                                containerColor = Color(0xFF25D366),
                                                contentColor = Color(0xFF091418),
                                                onClick = { onAnswer(false) }
                                            )
                                        } else {
                                            CallRoundAction(
                                                icon = Icons.Rounded.Call,
                                                label = "WhatsApp",
                                                containerColor = Color(0xFF25D366),
                                                contentColor = Color(0xFF091418),
                                                onClick = onOpenOfficialWa
                                            )
                                        }
                                    }
                                    if (state.canAnswerInApp && state.isVideo) {
                                        OutlinedButton(onClick = { onAnswer(true) }) {
                                            Text("Answer audio only", color = Color.White, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            else -> {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(34.dp))
                                        .background(Color(0xD917242C))
                                        .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(34.dp))
                                        .padding(horizontal = 20.dp, vertical = 14.dp)
                                ) {
                                    Box {
                                        CallPillToggle(
                                            icon = routeIcon(state.audioRoute),
                                            label = state.audioRoute.label,
                                            active = state.audioRoute != CallAudioRoute.EARPIECE,
                                            onClick = {
                                                if (state.availableRoutes.size <= 2) {
                                                    state.availableRoutes.firstOrNull { it != state.audioRoute }
                                                        ?.let(onSelectAudioRoute)
                                                } else {
                                                    showRouteMenu = true
                                                }
                                            }
                                        )
                                        DropdownMenu(
                                            expanded = showRouteMenu,
                                            onDismissRequest = { showRouteMenu = false }
                                        ) {
                                            state.availableRoutes.forEach { route ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = if (route == state.audioRoute) "${route.label} ✓" else route.label
                                                        )
                                                    },
                                                    onClick = {
                                                        showRouteMenu = false
                                                        onSelectAudioRoute(route)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    CallPillToggle(
                                        icon = if (state.isVideo && !state.isCameraOff) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                                        label = if (!state.isVideo) "Video" else if (state.isCameraOff) "Cam off" else "Cam on",
                                        active = state.isVideo && !state.isCameraOff,
                                        onClick = onToggleCamera
                                    )

                                    if (state.isVideo && !state.isCameraOff) {
                                        CallPillToggle(
                                            icon = Icons.Rounded.Cameraswitch,
                                            label = "Flip",
                                            active = false,
                                            onClick = onFlipCamera
                                        )
                                    }

                                    CallPillToggle(
                                        icon = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        label = if (state.isMuted) "Unmute" else "Mute",
                                        active = state.isMuted,
                                        onClick = onToggleMute
                                    )

                                    if (state.phase == CallPhase.ACTIVE) {
                                        CallPillToggle(
                                            icon = if (state.isRecording) Icons.Rounded.StopCircle else Icons.Rounded.FiberManualRecord,
                                            label = if (state.isRecording) "Stop Rec" else "Record",
                                            active = state.isRecording,
                                            onClick = onToggleRecording
                                        )
                                    }

                                    IconButton(
                                        onClick = onDeclineOrEnd,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE53935))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CallEnd,
                                            contentDescription = "End call",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCallBackdropContent(
    state: CallUiState,
    isInPip: Boolean
) {
    val mono = LocalWaMonoFamily.current
    val pulseTransition = rememberInfiniteTransition(label = "callAura")
    val auraScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auraScale"
    )
    val shouldPulse = state.phase == CallPhase.DIALING ||
        state.phase == CallPhase.INCOMING_RINGING ||
        state.phase == CallPhase.CONNECTING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (shouldPulse && !isInPip) {
                Box(
                    modifier = Modifier
                        .size(134.dp)
                        .scale(auraScale)
                        .clip(CircleShape)
                        .background(Color(0x3325D366))
                )
            }
            WhatsAppAvatar(
                jid = state.peerJid.ifBlank { "call" },
                avatarPath = state.peerAvatarPath,
                isGroup = state.isGroup,
                size = if (isInPip) 68.dp else 116.dp
            )
        }
        if (!isInPip) {
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = state.peerName.ifBlank { "WhatsApp Call" },
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (state.peerPhoneDigits.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "+${state.peerPhoneDigits}",
                    color = Color(0xB3D1D7DB),
                    fontSize = 14.sp,
                    fontFamily = mono
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = state.statusText.ifBlank { "Calling…" },
                color = if (state.phase == CallPhase.ACTIVE) Color(0xFF25D366) else Color(0xFFD1D7DB),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = if (state.phase == CallPhase.ACTIVE) mono else null
            )
        }
    }
}

@Composable
private fun CallRoundAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(containerColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CallPillToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF0A1418) else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xFFE0E7EB),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

private fun applyLocalPreviewTransform(view: TextureView, width: Int, height: Int) {
    // Camera2 supplies the sensor transform to TextureView. Compensate for screen rotation.
    val degrees = when (view.display?.rotation) {
        Surface.ROTATION_90 -> 270
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 90
        else -> 0
    }
    applyVideoTextureTransform(view, width, height, 480, 640, degrees, fillCrop = true)
}

private fun applyVideoTextureTransform(
    textureView: TextureView,
    viewWidth: Int,
    viewHeight: Int,
    videoWidth: Int,
    videoHeight: Int,
    cwRotationDeg: Int,
    fillCrop: Boolean
) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val vw = viewWidth.toFloat()
    val vh = viewHeight.toFloat()
    val cx = vw / 2f
    val cy = vh / 2f
    val rot = ((cwRotationDeg % 360) + 360) % 360
    val rotatedW = if (rot == 90 || rot == 270) videoHeight.toFloat() else videoWidth.toFloat()
    val rotatedH = if (rot == 90 || rot == 270) videoWidth.toFloat() else videoHeight.toFloat()

    val matrix = Matrix()
    // Undo TextureView's default full-bounds stretch back to true decoded pixel dimensions
    matrix.setScale(videoWidth.toFloat() / vw, videoHeight.toFloat() / vh, cx, cy)
    if (rot != 0) {
        matrix.postRotate(rot.toFloat(), cx, cy)
    }
    val uniformScale = if (fillCrop) {
        maxOf(vw / rotatedW, vh / rotatedH)
    } else {
        minOf(vw / rotatedW, vh / rotatedH)
    }
    matrix.postScale(uniformScale, uniformScale, cx, cy)
    textureView.setTransform(matrix)
}

private fun routeIcon(route: CallAudioRoute): ImageVector = when (route) {
    CallAudioRoute.SPEAKER -> Icons.Rounded.VolumeUp
    CallAudioRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    CallAudioRoute.WIRED_HEADSET -> Icons.Rounded.HeadsetMic
    CallAudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
}
