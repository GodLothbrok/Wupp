package com.wa.beastclient.ui.components

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.ui.theme.LocalWaColors

enum class ExpressiveSegmentPosition {
    SINGLE,
    TOP,
    MIDDLE,
    BOTTOM
}

fun expressiveSegmentShape(position: ExpressiveSegmentPosition): RoundedCornerShape = when (position) {
    ExpressiveSegmentPosition.SINGLE -> RoundedCornerShape(24.dp)
    ExpressiveSegmentPosition.TOP -> RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 6.dp,
        bottomEnd = 6.dp
    )
    ExpressiveSegmentPosition.MIDDLE -> RoundedCornerShape(8.dp)
    ExpressiveSegmentPosition.BOTTOM -> RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 6.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.98f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "expressive_press_scale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun ExpressiveSegmentedGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalWaColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = colors.accentGreen,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveIconBadge(
    icon: ImageVector,
    contentDescription: String?,
    active: Boolean,
    modifier: Modifier = Modifier,
    tintOverride: Color? = null
) {
    val colors = LocalWaColors.current
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()
    val inactiveShape = RoundedCornerShape(14.dp)

    val bg by animateColorAsState(
        targetValue = when {
            tintOverride != null -> tintOverride.copy(alpha = 0.18f)
            active -> colors.accentGreen.copy(alpha = 0.20f)
            else -> colors.textSecondary.copy(alpha = 0.11f)
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "badge_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = when {
            tintOverride != null -> tintOverride
            active -> colors.accentGreen
            else -> colors.textSecondary
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "badge_tint"
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1.05f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "badge_scale"
    )

    Box(
        modifier = modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(if (active) cookieShape else inactiveShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalWaColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedIconColor = colors.accentGreen,
            checkedTrackColor = colors.accentGreen,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedIconColor = colors.elevatedBar,
            uncheckedTrackColor = colors.pillInactiveBg,
            uncheckedBorderColor = colors.textSecondary.copy(alpha = 0.42f)
        )
    )
}

@Composable
fun ExpressiveSegmentedContainer(
    position: ExpressiveSegmentPosition,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalWaColors.current
    val shape = expressiveSegmentShape(position)
    val containerColor = if (highlighted) {
        colors.pillActiveBg.copy(alpha = if (colors.isDark) 0.52f else 0.72f)
    } else {
        colors.elevatedBar.copy(alpha = if (colors.isDark) 0.90f else 0.98f)
    }
    val borderColor = if (highlighted) {
        colors.accentGreen.copy(alpha = 0.28f)
    } else if (colors.isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        colors.divider
    }

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .fillMaxWidth()
            .border(0.75.dp, borderColor, shape)
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSegmentedSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: ExpressiveSegmentPosition = ExpressiveSegmentPosition.MIDDLE,
    enabled: Boolean = true,
    onInfoClick: (() -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = expressiveSegmentShape(position)

    ExpressiveSegmentedContainer(
        position = position,
        highlighted = false,
        modifier = Modifier
            .then(if (enabled) Modifier.expressivePressScale(interactionSource) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onCheckedChange(!checked) }
                )
                .padding(horizontal = 14.dp, vertical = if (subtitle.isBlank()) 11.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveIconBadge(
                icon = icon,
                contentDescription = null,
                active = checked && enabled
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onInfoClick != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onInfoClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Info about $title",
                                tint = colors.textSecondary.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            ExpressiveSwitch(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSegmentedActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    badgeText: String? = null,
    position: ExpressiveSegmentPosition = ExpressiveSegmentPosition.MIDDLE,
    enabled: Boolean = true,
    onInfoClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = LocalWaColors.current
    val highlighted = !badgeText.isNullOrBlank() && enabled
    val interactionSource = remember { MutableInteractionSource() }
    val shape = expressiveSegmentShape(position)

    ExpressiveSegmentedContainer(
        position = position,
        highlighted = false,
        modifier = Modifier
            .then(if (enabled) Modifier.expressivePressScale(interactionSource) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.46f }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = if (subtitle.isBlank()) 11.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveIconBadge(
                icon = icon,
                contentDescription = null,
                active = highlighted
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (onInfoClick != null) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onInfoClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Info about $title",
                                tint = colors.textSecondary.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!badgeText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accentGreen)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color(0xFF0B141A),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
fun ExpressiveSegmentedSliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0,
    position: ExpressiveSegmentPosition = ExpressiveSegmentPosition.MIDDLE,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalWaColors.current
    val shape = expressiveSegmentShape(position)
    ExpressiveSegmentedContainer(position = position) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressiveIconBadge(
                    icon = icon,
                    contentDescription = title,
                    active = true
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = colors.textPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = valueLabel,
                        color = colors.accentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = colors.accentGreen,
                    activeTrackColor = colors.accentGreen,
                    inactiveTrackColor = colors.pillInactiveBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveTileShape(index: Int): Shape = when (index % 4) {
    0 -> MaterialShapes.Cookie9Sided.toShape()
    1 -> MaterialShapes.Sunny.toShape()
    2 -> MaterialShapes.Clover4Leaf.toShape()
    else -> MaterialShapes.SoftBurst.toShape()
}
