package com.wa.beastclient.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.WuppAccentColor
import com.wa.beastclient.ui.theme.WuppFontStyle
import com.wa.beastclient.ui.theme.WuppThemeMode
import com.wa.beastclient.ui.theme.colorsForTheme
import com.wa.beastclient.ui.theme.contrastingContentColor
import com.wa.beastclient.ui.theme.resolvePlatformDynamicColorScheme

@Composable
fun ThemeSelectorGrid(
    selectedTheme: WuppThemeMode,
    onSelectTheme: (WuppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    selectedAccent: WuppAccentColor = WuppAccentColor.DEFAULT,
    onSelectAccent: ((WuppAccentColor) -> Unit)? = null,
    pureBlackOled: Boolean = false,
    onTogglePureBlackOled: ((Boolean) -> Unit)? = null,
    dynamicMaterialYou: Boolean = false,
    onToggleDynamicMaterialYou: ((Boolean) -> Unit)? = null,
    bubbleCornerRadiusDp: Int = 17,
    onBubbleCornerRadiusChange: ((Int) -> Unit)? = null,
    customOutgoingBubbleArgb: Long = 0L,
    customIncomingBubbleArgb: Long = 0L,
    onCustomBubbleColorsChange: ((Long, Long) -> Unit)? = null,
    selectedFontStyle: WuppFontStyle = WuppFontStyle.ROBO_CLEAN,
    hasImportedFont: Boolean = false,
    onSelectFontStyle: ((WuppFontStyle) -> Unit)? = null,
    onImportCustomFontUri: ((Uri) -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportCustomFontUri?.invoke(uri)
        }
    }

    val bubbleSwatches = remember {
        listOf(
            0L to "Theme",
            0xFF005C4BL to "Emerald",
            0xFF1E3A8AL to "Indigo",
            0xFF6B21A8L to "Plum",
            0xFF831843L to "Rose",
            0xFF78350FL to "Bronze",
            0xFF15222BL to "Slate",
            0xFF0A0A0AL to "Pitch"
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Horizontal scrollable or compact 2-col theme grid inside a constrained box when 10 themes exist
        val rows = WuppThemeMode.entries.chunked(2)
        rows.forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowModes.forEach { mode ->
                    ThemePreviewCard(
                        mode = mode,
                        selected = mode == selectedTheme,
                        pureBlackOled = pureBlackOled,
                        dynamicMaterialYou = dynamicMaterialYou,
                        onClick = { onSelectTheme(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (onToggleDynamicMaterialYou != null) {
            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.appSurface.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Material You Dynamic Colors",
                        color = colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (dynamicSupported) {
                            "Syncs backgrounds, buttons, bubbles & accents with phone wallpaper"
                        } else {
                            "Requires Android 12 or newer"
                        },
                        color = colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = dynamicMaterialYou && dynamicSupported,
                    enabled = dynamicSupported,
                    onCheckedChange = onToggleDynamicMaterialYou,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accentGreen,
                        checkedThumbColor = colors.accentGreen.contrastingContentColor()
                    )
                )
            }
        }

        if (onTogglePureBlackOled != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.appSurface.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pure Black OLED Mode",
                        color = colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "True #000000 black surfaces & chrome",
                        color = colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = pureBlackOled,
                    onCheckedChange = onTogglePureBlackOled,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.accentGreen,
                        checkedThumbColor = Color.White
                    )
                )
            }
        }

        if (onSelectAccent != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (dynamicMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "Accent Color · Material You Dynamic (active)"
                    } else {
                        "Accent Color · ${selectedAccent.label}"
                    },
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    val themeBase = colorsForTheme(
                        mode = selectedTheme,
                        accent = WuppAccentColor.DEFAULT,
                        pureBlackOled = pureBlackOled,
                        dynamicScheme = if (dynamicMaterialYou) {
                            resolvePlatformDynamicColorScheme(context, pureBlackOled || colorsForTheme(selectedTheme).isDark)
                        } else {
                            null
                        }
                    )
                    WuppAccentColor.entries.forEach { accent ->
                        val isSelected = accent == selectedAccent && !(dynamicMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        val swatchColor = if (accent == WuppAccentColor.DEFAULT) {
                            themeBase.brightGreen
                        } else {
                            Color(accent.brightArgb)
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) colors.textPrimary else colors.divider,
                                    shape = CircleShape
                                )
                                .clickable {
                                    if (dynamicMaterialYou) onToggleDynamicMaterialYou?.invoke(false)
                                    onSelectAccent(accent)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = accent.label,
                                    tint = swatchColor.contrastingContentColor(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (onCustomBubbleColorsChange != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Outgoing Bubble Tint",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val defaultPreview = colorsForTheme(selectedTheme, selectedAccent, pureBlackOled = pureBlackOled).outgoingBubble
                    bubbleSwatches.forEach { (argb, label) ->
                        val isSelected = customOutgoingBubbleArgb == argb
                        val chipColor = if (argb == 0L) defaultPreview else Color(argb)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(chipColor)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) colors.brightGreen else colors.divider,
                                    shape = CircleShape
                                )
                                .clickable { onCustomBubbleColorsChange(argb, customIncomingBubbleArgb) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Rounded.CheckCircle, label, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        if (onBubbleCornerRadiusChange != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bubble Corner Roundness",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${bubbleCornerRadiusDp}dp",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = bubbleCornerRadiusDp.toFloat(),
                    onValueChange = { onBubbleCornerRadiusChange(it.toInt().coerceIn(4, 24)) },
                    valueRange = 4f..24f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentGreen,
                        activeTrackColor = colors.accentGreen
                    )
                )
            }
        }

        if (onSelectFontStyle != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Typography · ${selectedFontStyle.label}",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WuppFontStyle.entries.forEach { fontOpt ->
                        if (fontOpt == WuppFontStyle.CUSTOM && !hasImportedFont && onImportCustomFontUri == null) return@forEach
                        val isSelected = selectedFontStyle == fontOpt
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (fontOpt == WuppFontStyle.CUSTOM && !hasImportedFont && onImportCustomFontUri != null) {
                                    AppLockManager.suspendNextBackgroundLock()
                                    fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "*/*"))
                                } else {
                                    onSelectFontStyle(fontOpt)
                                }
                            },
                            label = { Text(fontOpt.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.pillActiveBg,
                                selectedLabelColor = colors.pillActiveText
                            )
                        )
                    }
                    if (onImportCustomFontUri != null) {
                        OutlinedButton(
                            onClick = {
                                AppLockManager.suspendNextBackgroundLock()
                                fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "*/*"))
                            },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.TextFields, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (hasImportedFont) "Replace .ttf" else "Import .ttf/.otf", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemePickerDialog(
    selectedTheme: WuppThemeMode,
    onSelectTheme: (WuppThemeMode) -> Unit,
    onDismiss: () -> Unit,
    selectedAccent: WuppAccentColor = WuppAccentColor.DEFAULT,
    onSelectAccent: ((WuppAccentColor) -> Unit)? = null,
    pureBlackOled: Boolean = false,
    onTogglePureBlackOled: ((Boolean) -> Unit)? = null,
    dynamicMaterialYou: Boolean = false,
    onToggleDynamicMaterialYou: ((Boolean) -> Unit)? = null,
    bubbleCornerRadiusDp: Int = 17,
    onBubbleCornerRadiusChange: ((Int) -> Unit)? = null,
    customOutgoingBubbleArgb: Long = 0L,
    customIncomingBubbleArgb: Long = 0L,
    onCustomBubbleColorsChange: ((Long, Long) -> Unit)? = null,
    selectedFontStyle: WuppFontStyle = WuppFontStyle.ROBO_CLEAN,
    hasImportedFont: Boolean = false,
    onSelectFontStyle: ((WuppFontStyle) -> Unit)? = null,
    onImportCustomFontUri: ((Uri) -> Unit)? = null
) {
    val colors = LocalWaColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = "Themes, OLED & Fonts",
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ThemeSelectorGrid(
                    selectedTheme = selectedTheme,
                    onSelectTheme = onSelectTheme,
                    selectedAccent = selectedAccent,
                    onSelectAccent = onSelectAccent,
                    pureBlackOled = pureBlackOled,
                    onTogglePureBlackOled = onTogglePureBlackOled,
                    dynamicMaterialYou = dynamicMaterialYou,
                    onToggleDynamicMaterialYou = onToggleDynamicMaterialYou,
                    bubbleCornerRadiusDp = bubbleCornerRadiusDp,
                    onBubbleCornerRadiusChange = onBubbleCornerRadiusChange,
                    customOutgoingBubbleArgb = customOutgoingBubbleArgb,
                    customIncomingBubbleArgb = customIncomingBubbleArgb,
                    onCustomBubbleColorsChange = onCustomBubbleColorsChange,
                    selectedFontStyle = selectedFontStyle,
                    hasImportedFont = hasImportedFont,
                    onSelectFontStyle = onSelectFontStyle,
                    onImportCustomFontUri = onImportCustomFontUri
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.brightGreen)
            }
        }
    )
}

@Composable
private fun ThemePreviewCard(
    mode: WuppThemeMode,
    selected: Boolean,
    pureBlackOled: Boolean = false,
    dynamicMaterialYou: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalWaColors.current
    val preview = colorsForTheme(
        mode = mode,
        pureBlackOled = pureBlackOled,
        dynamicScheme = if (dynamicMaterialYou) {
            resolvePlatformDynamicColorScheme(context, pureBlackOled || colorsForTheme(mode).isDark)
        } else {
            null
        }
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) appColors.brightGreen else appColors.divider,
        label = "themeBorder"
    )
    val cardBg by animateColorAsState(
        targetValue = if (selected) appColors.pillActiveBg.copy(alpha = 0.42f) else appColors.appSurface.copy(alpha = 0.45f),
        label = "themeCardBg"
    )

    val modeIcon = when (mode) {
        WuppThemeMode.LIGHT -> Icons.Rounded.LightMode
        WuppThemeMode.DARK -> Icons.Rounded.DarkMode
        WuppThemeMode.AMOLED -> Icons.Rounded.NightlightRound
        WuppThemeMode.AURORA -> Icons.Rounded.AutoAwesome
        WuppThemeMode.SYNTHWAVE -> Icons.Rounded.Bolt
        WuppThemeMode.SAKURA -> Icons.Rounded.LocalFlorist
        WuppThemeMode.NOIR_GOLD -> Icons.Rounded.WorkspacePremium
        WuppThemeMode.PAPER_INK -> Icons.Rounded.Description
        WuppThemeMode.MATCHA -> Icons.Rounded.Eco
        WuppThemeMode.GLACIER -> Icons.Rounded.AcUnit
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = cardBg
    ) {
        Column(
            modifier = Modifier.padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live miniature chat window preview in that theme's exact palette
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(preview.chatCanvas)
                    .border(
                        width = 0.6.dp,
                        color = preview.divider,
                        shape = RoundedCornerShape(11.dp)
                    )
            ) {
                if (mode == WuppThemeMode.AURORA) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x3D6D28D9),
                                        Color.Transparent,
                                        Color(0x2800E5B8)
                                    )
                                )
                            )
                    )
                }

                Column(modifier = Modifier.matchParentSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(preview.elevatedBar)
                            .padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(preview.accentGreen)
                        )
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(preview.textPrimary.copy(alpha = 0.72f))
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(preview.brightGreen)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 7.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(preview.incomingBubble)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(34.dp)
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(preview.textSecondary.copy(alpha = 0.65f))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Row(
                                modifier = Modifier
                                    .width(66.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(preview.outgoingBubble)
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(38.dp)
                                        .height(3.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(preview.textPrimary.copy(alpha = 0.85f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .clip(CircleShape)
                                        .background(preview.blueTick)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = modeIcon,
                    contentDescription = null,
                    tint = if (selected) appColors.brightGreen else preview.accentGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = mode.label,
                    color = appColors.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = appColors.brightGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = mode.tagline,
                color = appColors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WallpaperPickerDialog(
    currentUri: String,
    currentDim: Float,
    showDoodleOnPresets: Boolean,
    currentScale: Float = 1f,
    currentOffsetX: Float = 0f,
    currentOffsetY: Float = 0f,
    onSelectUri: (String) -> Unit,
    onPickCustomImage: (Uri) -> Unit,
    onDimChange: (Float) -> Unit,
    onToggleDoodle: (Boolean) -> Unit,
    onPositionChange: ((scale: Float, offsetX: Float, offsetY: Float) -> Unit)? = null,
    onDismiss: () -> Unit,
    chatTitle: String? = null,
    hasChatOverride: Boolean = false,
    chatUri: String = currentUri,
    chatDim: Float = currentDim,
    chatShowDoodle: Boolean = showDoodleOnPresets,
    chatScale: Float = currentScale,
    chatOffsetX: Float = currentOffsetX,
    chatOffsetY: Float = currentOffsetY,
    onSelectChatUri: ((String) -> Unit)? = null,
    onPickChatCustomImage: ((Uri) -> Unit)? = null,
    onChatDimChange: ((Float) -> Unit)? = null,
    onToggleChatDoodle: ((Boolean) -> Unit)? = null,
    onChatPositionChange: ((scale: Float, offsetX: Float, offsetY: Float) -> Unit)? = null,
    onResetChatToGlobal: (() -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val supportsPerChat = !chatTitle.isNullOrBlank() && onSelectChatUri != null && onPickChatCustomImage != null
    var editThisChat by remember(chatTitle) { mutableStateOf(supportsPerChat) }

    val activeUri = if (supportsPerChat && editThisChat) chatUri else currentUri
    val activeDim = if (supportsPerChat && editThisChat) chatDim else currentDim
    val activeShowDoodle = if (supportsPerChat && editThisChat) chatShowDoodle else showDoodleOnPresets
    val activeScale = (if (supportsPerChat && editThisChat) chatScale else currentScale).coerceIn(1f, 3f)
    val activeOffsetX = (if (supportsPerChat && editThisChat) chatOffsetX else currentOffsetX).coerceIn(-1f, 1f)
    val activeOffsetY = (if (supportsPerChat && editThisChat) chatOffsetY else currentOffsetY).coerceIn(-1f, 1f)
    var previewSize by remember { mutableStateOf(IntSize.Zero) }

    val updatePosition: (Float, Float, Float) -> Unit = { s, ox, oy ->
        val ns = s.coerceIn(1f, 3f)
        val nox = ox.coerceIn(-1f, 1f)
        val noy = oy.coerceIn(-1f, 1f)
        if (supportsPerChat && editThisChat) {
            onChatPositionChange?.invoke(ns, nox, noy)
        } else {
            onPositionChange?.invoke(ns, nox, noy)
        }
    }
    val latestScale by rememberUpdatedState(activeScale)
    val latestOffsetX by rememberUpdatedState(activeOffsetX)
    val latestOffsetY by rememberUpdatedState(activeOffsetY)
    val latestUpdatePosition by rememberUpdatedState(updatePosition)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            if (supportsPerChat && editThisChat) {
                onPickChatCustomImage?.invoke(uri)
            } else {
                onPickCustomImage(uri)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Text(
                text = if (supportsPerChat && editThisChat) "Wallpaper for $chatTitle" else "Chat Wallpaper",
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (supportsPerChat) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.chatCanvas)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val scopeOptions = listOf(true to "This chat", false to "All chats")
                        scopeOptions.forEach { (isThisChat, label) ->
                            val selected = editThisChat == isThisChat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) colors.accentGreen else Color.Transparent)
                                    .clickable { editThisChat = isThisChat }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else colors.textSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                    if (editThisChat && !hasChatOverride) {
                        Text(
                            text = "Currently using global wallpaper (${describeChatWallpaper(currentUri)}). Pick a photo or preset to customize only this chat.",
                            color = colors.textSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Live chat preview banner (pinch-to-zoom & drag-to-pan when custom photo active)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.chatCanvas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (activeUri.startsWith("file:")) 168.dp else 124.dp)
                        .border(1.dp, colors.divider, RoundedCornerShape(14.dp))
                        .onSizeChanged { previewSize = it }
                        .then(
                            if (activeUri.startsWith("file:")) {
                                Modifier.pointerInput(activeUri, editThisChat) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val w = previewSize.width.coerceAtLeast(1).toFloat()
                                        val h = previewSize.height.coerceAtLeast(1).toFloat()
                                        val nextScale = (latestScale * zoom).coerceIn(1f, 3f)
                                        val nextX = (latestOffsetX + pan.x / w).coerceIn(-0.85f, 0.85f)
                                        val nextY = (latestOffsetY + pan.y / h).coerceIn(-0.85f, 0.85f)
                                        latestUpdatePosition(nextScale, nextX, nextY)
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    DoodleBackground(
                        wallpaperUriOverride = activeUri,
                        wallpaperDimOverride = activeDim,
                        showDoodleOverride = activeShowDoodle,
                        wallpaperScaleOverride = activeScale,
                        wallpaperOffsetXOverride = activeOffsetX,
                        wallpaperOffsetYOverride = activeOffsetY,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.incomingBubble
                            ) {
                                Text(
                                    text = if (supportsPerChat && editThisChat) "Custom background for $chatTitle" else "Custom background preview",
                                    color = colors.textPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.outgoingBubble
                                ) {
                                    Text(
                                        text = if (supportsPerChat && editThisChat && !hasChatOverride) {
                                            "Global: ${describeChatWallpaper(activeUri)}"
                                        } else {
                                            describeChatWallpaper(activeUri)
                                        },
                                        color = colors.textPrimary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Gallery + Reset action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            AppLockManager.suspendNextBackgroundLock()
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("From gallery", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(
                        onClick = {
                            if (supportsPerChat && editThisChat) {
                                onResetChatToGlobal?.invoke() ?: onSelectChatUri?.invoke("")
                            } else {
                                onSelectUri("")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (supportsPerChat && editThisChat) "Use global" else "Default",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Curated color/gradient presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Presets",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ChatWallpaperPresets.forEach { preset ->
                            val isSelected = activeUri == preset.storageUri
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable {
                                    if (supportsPerChat && editThisChat) {
                                        onSelectChatUri?.invoke(preset.storageUri)
                                    } else {
                                        onSelectUri(preset.storageUri)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Brush.verticalGradient(listOf(preset.topColor, preset.bottomColor)))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) colors.brightGreen else colors.divider,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = colors.brightGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = preset.label,
                                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }

                if (activeUri.startsWith("file:")) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ZoomIn,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Zoom (${String.format("%.1f", activeScale)}x) • drag preview to pan",
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            if (activeScale != 1f || activeOffsetX != 0f || activeOffsetY != 0f) {
                                Text(
                                    text = "Reset fit",
                                    color = colors.brightGreen,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { updatePosition(1f, 0f, 0f) }
                                )
                            }
                        }
                        Slider(
                            value = activeScale.coerceIn(1f, 3f),
                            onValueChange = { scaleVal ->
                                updatePosition(scaleVal, activeOffsetX, activeOffsetY)
                            },
                            valueRange = 1f..3f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.brightGreen,
                                activeTrackColor = colors.accentGreen,
                                inactiveTrackColor = colors.divider
                            )
                        )
                        Text(
                            text = "Overlay dimness (${(activeDim * 100).toInt()}%)",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = activeDim.coerceIn(0f, 0.75f),
                            onValueChange = { dimVal ->
                                if (supportsPerChat && editThisChat) {
                                    onChatDimChange?.invoke(dimVal)
                                } else {
                                    onDimChange(dimVal)
                                }
                            },
                            valueRange = 0f..0.75f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.brightGreen,
                                activeTrackColor = colors.accentGreen,
                                inactiveTrackColor = colors.divider
                            )
                        )
                    }
                } else if (activeUri.startsWith("preset:")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Show doodle overlay",
                            color = colors.textPrimary,
                            fontSize = 13.5.sp
                        )
                        Switch(
                            checked = activeShowDoodle,
                            onCheckedChange = { checked ->
                                if (supportsPerChat && editThisChat) {
                                    onToggleChatDoodle?.invoke(checked)
                                } else {
                                    onToggleDoodle(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accentGreen
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = colors.brightGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
