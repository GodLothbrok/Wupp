package com.wa.beastclient.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.util.Locale

@Composable
fun ProfilePhotoLightbox(
    jid: String,
    title: String,
    avatarPath: String?,
    onDismiss: () -> Unit,
    onRequestFullAvatar: (() -> Unit)? = null,
    onSaveAvatar: (() -> Unit)? = null
) {
    val bmp = remember(avatarPath) {
        avatarPath?.let { path ->
            File(path).takeIf { it.exists() && it.length() > 0L }
                ?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
        }
    }

    val isLowResPreview = bmp == null || (bmp.width <= 160 && bmp.height <= 160)
    LaunchedEffect(jid, avatarPath) {
        if (isLowResPreview) {
            onRequestFullAvatar?.invoke()
        }
    }

    var scale by remember(avatarPath) { mutableFloatStateOf(1f) }
    var offset by remember(avatarPath) { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        offset = if (nextScale > 1.02f) {
            Offset(
                x = (offset.x + panChange.x).coerceIn(-600f, 600f),
                y = (offset.y + panChange.y).coerceIn(-600f, 600f)
            )
        } else {
            Offset.Zero
        }
    }

    val headerTitle = when {
        jid.endsWith("@g.us") -> "Group icon"
        jid.endsWith("@newsletter") -> title.ifBlank { "Channel icon" }
        else -> title.ifBlank { jid.substringBefore("@") }
    }

    val darkOrbBg = Color(0xFF14171A)
    val darkOrbBorder = Color.White.copy(alpha = 0.08f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(avatarPath) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.15f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.4f
                                }
                            }
                        )
                    }
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = headerTitle,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(144.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF12161B))
                                .border(0.75.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (jid.endsWith("@g.us")) Icons.Rounded.Group else Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color(0xFF8C969E),
                                modifier = Modifier.size(68.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No profile photo",
                            color = Color(0xFF8C969E),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Floating Top Scrim + Circular Dark Back Chevron & Save Orb
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .expressivePressScale(backInteraction, pressedScale = 0.88f)
                        .clip(CircleShape)
                        .background(darkOrbBg)
                        .border(0.6.dp, darkOrbBorder, CircleShape)
                        .clickable(
                            interactionSource = backInteraction,
                            indication = null,
                            onClick = onDismiss
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (jid.endsWith("@g.us") && title.isNotBlank()) {
                        Text(
                            text = title,
                            color = Color(0xFFB1B8BE),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (bmp != null && onSaveAvatar != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val saveInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .expressivePressScale(saveInteraction, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(darkOrbBg)
                            .border(0.65.dp, darkOrbBorder, CircleShape)
                            .clickable(
                                interactionSource = saveInteraction,
                                indication = null,
                                onClick = onSaveAvatar
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Save photo",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom Zoom Reset Pill (shown when pinched or double-tapped)
            AnimatedVisibility(
                visible = scale > 1.05f && bmp != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 26.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(darkOrbBg)
                        .border(0.65.dp, darkOrbBorder, CircleShape)
                        .clickable {
                            scale = 1f
                            offset = Offset.Zero
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", scale)}× · Tap to reset",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Bottom Loading HD Photo Pill
            if (isLowResPreview && bmp != null && onRequestFullAvatar != null && scale <= 1.05f) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 26.dp)
                        .clip(CircleShape)
                        .background(darkOrbBg)
                        .border(0.65.dp, darkOrbBorder, CircleShape)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                    LoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF25D366)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading HD photo...",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
