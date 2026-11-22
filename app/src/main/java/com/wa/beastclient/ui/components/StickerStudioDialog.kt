package com.wa.beastclient.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wa.beastclient.ui.theme.LocalWaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class StickerStudioSource(val label: String) {
    PHOTO("Photo"),
    TEXT("Text & Emoji"),
    DRAW("Draw")
}

enum class StickerCutShape(val label: String) {
    SQUARE("Full"),
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    HEART("Heart")
}

private data class StudioStroke(
    val colorArgb: Int,
    val widthPx: Float,
    val points: List<Offset>
)

internal fun shouldEraseBackgroundPixel(
    r: Int,
    g: Int,
    b: Int,
    bgR: Int,
    bgG: Int,
    bgB: Int,
    tolerance: Int
): Boolean {
    val dr = r - bgR
    val dg = g - bgG
    val db = b - bgB
    val distSq = dr * dr + dg * dg + db * db
    return distSq <= tolerance * tolerance * 3
}

/**
 * Flood-fills connected border pixels within [tolerance] of edge corner colors into transparent pixels,
 * preserving inner details cleanly on-device with zero network dependencies.
 */
internal fun stripCornerMatchedBackground(src: Bitmap, tolerance: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 2 || h <= 2) return src.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    val cornerSamples = intArrayOf(
        pixels[0],
        pixels[w - 1],
        pixels[(h - 1) * w],
        pixels[h * w - 1]
    )
    var sumR = 0
    var sumG = 0
    var sumB = 0
    for (c in cornerSamples) {
        sumR += (c ushr 16) and 0xFF
        sumG += (c ushr 8) and 0xFF
        sumB += c and 0xFF
    }
    val bgR = sumR / 4
    val bgG = sumG / 4
    val bgB = sumB / 4

    val visited = BooleanArray(w * h)
    val queue = IntArray(w * h)
    var head = 0
    var tail = 0

    fun enqueueIfMatches(idx: Int) {
        if (visited[idx]) return
        val px = pixels[idx]
        val alpha = (px ushr 24) and 0xFF
        if (alpha < 16) {
            visited[idx] = true
            queue[tail++] = idx
            return
        }
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        if (shouldEraseBackgroundPixel(r, g, b, bgR, bgG, bgB, tolerance)) {
            visited[idx] = true
            pixels[idx] = 0x00000000
            queue[tail++] = idx
        }
    }

    for (x in 0 until w) {
        enqueueIfMatches(x)
        enqueueIfMatches((h - 1) * w + x)
    }
    for (y in 1 until h - 1) {
        enqueueIfMatches(y * w)
        enqueueIfMatches(y * w + (w - 1))
    }

    while (head < tail) {
        val cur = queue[head++]
        val x = cur % w
        val y = cur / w
        if (x > 0) enqueueIfMatches(cur - 1)
        if (x + 1 < w) enqueueIfMatches(cur + 1)
        if (y > 0) enqueueIfMatches(cur - w)
        if (y + 1 < h) enqueueIfMatches(cur + w)
    }

    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, w, 0, 0, w, h)
    }
}

private fun decodeScaledStickerBitmap(context: Context, uri: Uri, maxDim: Int = 512): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val scale = min(maxDim.toFloat() / raw.width.toFloat(), maxDim.toFloat() / raw.height.toFloat())
    val outW = max(1, (raw.width * scale).toInt())
    val outH = max(1, (raw.height * scale).toInt())
    if (outW == raw.width && outH == raw.height) return raw
    return Bitmap.createScaledBitmap(raw, outW, outH, true).also {
        if (it !== raw) raw.recycle()
    }
}

private fun buildStickerClipPath(shape: StickerCutShape, size: Float, pad: Float): Path = Path().apply {
    val inner = size - pad * 2f
    val left = pad
    val top = pad
    val right = left + inner
    val bottom = top + inner
    when (shape) {
        StickerCutShape.SQUARE -> addRect(left, top, right, bottom, Path.Direction.CW)
        StickerCutShape.ROUNDED -> addRoundRect(RectF(left, top, right, bottom), inner * 0.16f, inner * 0.16f, Path.Direction.CW)
        StickerCutShape.CIRCLE -> addOval(RectF(left, top, right, bottom), Path.Direction.CW)
        StickerCutShape.HEART -> {
            val midX = size / 2f
            moveTo(midX, bottom - inner * 0.08f)
            cubicTo(
                left - inner * 0.08f,
                top + inner * 0.56f,
                left + inner * 0.06f,
                top + inner * 0.04f,
                midX,
                top + inner * 0.28f
            )
            cubicTo(
                right - inner * 0.06f,
                top + inner * 0.04f,
                right + inner * 0.08f,
                top + inner * 0.56f,
                midX,
                bottom - inner * 0.08f
            )
            close()
        }
    }
}

private fun applyWhiteDieCutOutline(content: Bitmap): Bitmap {
    val size = content.width
    val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val alphaMask = content.extractAlpha()
    val strokePx = (size * 0.024f).coerceAtLeast(8f)

    // Subtle elevation shadow under sticker cutout
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(95, 0, 0, 0)
        maskFilter = BlurMaskFilter(strokePx * 0.8f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawBitmap(alphaMask, 0f, strokePx * 0.3f, shadowPaint)

    // White die-cut ring around opaque edges
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    val steps = 16
    for (i in 0 until steps) {
        val angle = (2.0 * Math.PI * i) / steps
        val dx = (Math.cos(angle) * strokePx).toFloat()
        val dy = (Math.sin(angle) * strokePx).toFloat()
        canvas.drawBitmap(alphaMask, dx, dy, outlinePaint)
    }
    canvas.drawBitmap(content, 0f, 0f, null)
    alphaMask.recycle()
    return result
}

private fun renderStudioStickerBitmap(
    sourceMode: StickerStudioSource,
    photoBitmap: Bitmap?,
    cutShape: StickerCutShape,
    removeBg: Boolean,
    bgTolerance: Int,
    addOutline: Boolean,
    captionText: String,
    captionColorArgb: Int,
    strokes: List<StudioStroke>,
    drawCanvasPx: IntSize
): Bitmap {
    val size = 512
    val pad = 28f
    val rawLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(rawLayer)

    if (sourceMode == StickerStudioSource.PHOTO && photoBitmap != null) {
        val working = if (removeBg) stripCornerMatchedBackground(photoBitmap, bgTolerance) else photoBitmap
        val shaped = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val sc = Canvas(shaped)
        val clipPath = buildStickerClipPath(cutShape, size.toFloat(), pad)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
        sc.drawPath(clipPath, maskPaint)

        val innerSize = size - pad * 2f
        val scale = max(innerSize / working.width.toFloat(), innerSize / working.height.toFloat())
        val drawW = working.width * scale
        val drawH = working.height * scale
        val dx = (size - drawW) / 2f
        val dy = (size - drawH) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        val xferPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        sc.drawBitmap(working, matrix, xferPaint)
        canvas.drawBitmap(shaped, 0f, 0f, null)
        shaped.recycle()
        if (removeBg && working !== photoBitmap) working.recycle()
    } else if (sourceMode == StickerStudioSource.DRAW) {
        val sx = if (drawCanvasPx.width > 0) size.toFloat() / drawCanvasPx.width else 1f
        val sy = if (drawCanvasPx.height > 0) size.toFloat() / drawCanvasPx.height else 1f
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.colorArgb
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.widthPx * ((sx + sy) * 0.5f)
            }
            if (stroke.points.size == 1) {
                val pt = stroke.points.first()
                canvas.drawPoint(pt.x * sx, pt.y * sy, paint)
            } else {
                val p = Path()
                stroke.points.forEachIndexed { idx, pt ->
                    val px = pt.x * sx
                    val py = pt.y * sy
                    if (idx == 0) p.moveTo(px, py) else p.lineTo(px, py)
                }
                canvas.drawPath(p, paint)
            }
        }
    }

    val trimmedCaption = captionText.trim()
    if (trimmedCaption.isNotEmpty() || sourceMode == StickerStudioSource.TEXT) {
        val label = trimmedCaption.ifEmpty { "😎" }
        val isLargeCenter = sourceMode == StickerStudioSource.TEXT
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = captionColorArgb
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (isLargeCenter) {
                when {
                    label.length <= 4 -> 185f
                    label.length <= 12 -> 86f
                    else -> 58f
                }
            } else {
                when {
                    label.length <= 10 -> 64f
                    label.length <= 20 -> 48f
                    else -> 36f
                }
            }
        }
        val outlineTextPaint = Paint(textPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = textPaint.textSize * 0.16f
            strokeJoin = Paint.Join.ROUND
            color = android.graphics.Color.WHITE
        }
        val yPos = if (isLargeCenter) {
            size / 2f - ((textPaint.descent() + textPaint.ascent()) / 2f)
        } else {
            size - 56f
        }
        canvas.drawText(label, size / 2f, yPos, outlineTextPaint)
        canvas.drawText(label, size / 2f, yPos, textPaint)
    }

    if (!addOutline) return rawLayer
    return applyWhiteDieCutOutline(rawLayer).also { rawLayer.recycle() }
}

/**
 * Full offline Sticker Studio dialog for creating 512x512 WebP stickers from photos, text/emoji,
 * or freehand drawing, with local corner-matched background removal and die-cut outlines.
 */
@Composable
fun StickerStudioDialog(
    initialPhotoUri: Uri? = null,
    canSendNow: Boolean = false,
    onSaveToPack: (Bitmap, String) -> Unit,
    onSaveAndSend: ((Bitmap, String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWaColors.current
    val scope = rememberCoroutineScope()

    var sourceMode by remember { mutableStateOf(StickerStudioSource.PHOTO) }
    var photoUri by remember(initialPhotoUri) { mutableStateOf(initialPhotoUri) }
    var sourcePhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cutShape by remember { mutableStateOf(StickerCutShape.ROUNDED) }
    var removeBg by remember { mutableStateOf(false) }
    var bgTolerance by remember { mutableFloatStateOf(42f) }
    var addOutline by remember { mutableStateOf(true) }
    var captionText by remember { mutableStateOf("") }
    var selectedColorArgb by remember { mutableIntStateOf(android.graphics.Color.parseColor("#FF25D366")) }
    var brushWidth by remember { mutableFloatStateOf(14f) }
    var drawCanvasSize by remember { mutableStateOf(IntSize(512, 512)) }
    val strokes = remember { mutableStateListOf<StudioStroke>() }
    var activeStrokePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked != null) {
            photoUri = picked
            sourceMode = StickerStudioSource.PHOTO
        }
    }

    LaunchedEffect(photoUri) {
        sourcePhotoBitmap = photoUri?.let { uri ->
            withContext(Dispatchers.IO) { decodeScaledStickerBitmap(context, uri) }
        }
    }

    LaunchedEffect(
        sourceMode,
        sourcePhotoBitmap,
        cutShape,
        removeBg,
        bgTolerance,
        addOutline,
        captionText,
        selectedColorArgb,
        strokes.size,
        drawCanvasSize
    ) {
        val rendered = withContext(Dispatchers.Default) {
            renderStudioStickerBitmap(
                sourceMode = sourceMode,
                photoBitmap = sourcePhotoBitmap,
                cutShape = cutShape,
                removeBg = removeBg,
                bgTolerance = bgTolerance.toInt(),
                addOutline = addOutline,
                captionText = captionText,
                captionColorArgb = selectedColorArgb,
                strokes = strokes.toList(),
                drawCanvasPx = drawCanvasSize
            )
        }
        previewBitmap = rendered
    }

    val palette = listOf(
        android.graphics.Color.WHITE,
        android.graphics.Color.BLACK,
        android.graphics.Color.parseColor("#FF25D366"),
        android.graphics.Color.parseColor("#FF06B6D4"),
        android.graphics.Color.parseColor("#FFF43F5E"),
        android.graphics.Color.parseColor("#FFF59E0B"),
        android.graphics.Color.parseColor("#FFA855F7")
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = colors.elevatedBar
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Sticker Studio",
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create crisp transparent WebP stickers on-device",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close", tint = colors.textSecondary)
                    }
                }

                // Mode Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StickerStudioSource.entries.forEach { mode ->
                        val selected = sourceMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { sourceMode = mode },
                            label = { Text(mode.label, fontSize = 12.5.sp) },
                            leadingIcon = {
                                val ic = when (mode) {
                                    StickerStudioSource.PHOTO -> Icons.Rounded.Image
                                    StickerStudioSource.TEXT -> Icons.Rounded.TextFields
                                    StickerStudioSource.DRAW -> Icons.Rounded.Brush
                                }
                                Icon(ic, null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.pillActiveBg,
                                selectedLabelColor = colors.pillActiveText
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Checkerboard Studio Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (colors.isDark) Color(0xFF141B21) else Color(0xFFE7ECF0))
                        .border(1.dp, colors.divider, RoundedCornerShape(18.dp))
                        .onSizeChanged { drawCanvasSize = it },
                    contentAlignment = Alignment.Center
                ) {
                    // Subtle checkerboard pattern showing transparency
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cell = 24.dp.toPx()
                        val alt = Color.Gray.copy(alpha = 0.14f)
                        val cols = (size.width / cell).toInt() + 1
                        val rows = (size.height / cell).toInt() + 1
                        for (r in 0 until rows) {
                            for (c in 0 until cols) {
                                if ((r + c) % 2 == 0) {
                                    drawRect(
                                        color = alt,
                                        topLeft = Offset(c * cell, r * cell),
                                        size = androidx.compose.ui.geometry.Size(cell, cell)
                                    )
                                }
                            }
                        }
                    }

                    if (sourceMode == StickerStudioSource.DRAW) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(selectedColorArgb, brushWidth) {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            activeStrokePoints = listOf(start)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            activeStrokePoints = activeStrokePoints + change.position
                                        },
                                        onDragEnd = {
                                            if (activeStrokePoints.isNotEmpty()) {
                                                strokes.add(
                                                    StudioStroke(
                                                        colorArgb = selectedColorArgb,
                                                        widthPx = brushWidth,
                                                        points = activeStrokePoints
                                                    )
                                                )
                                                activeStrokePoints = emptyList()
                                            }
                                        }
                                    )
                                }
                        ) {
                            val drawStrokeList = strokes.toList() + if (activeStrokePoints.isNotEmpty()) {
                                listOf(StudioStroke(selectedColorArgb, brushWidth, activeStrokePoints))
                            } else emptyList()
                            drawStrokeList.forEach { st ->
                                if (st.points.size == 1) {
                                    drawCircle(
                                        color = Color(st.colorArgb),
                                        radius = st.widthPx / 2f,
                                        center = st.points.first()
                                    )
                                } else {
                                    for (i in 0 until st.points.size - 1) {
                                        drawLine(
                                            color = Color(st.colorArgb),
                                            start = st.points[i],
                                            end = st.points[i + 1],
                                            strokeWidth = st.widthPx,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }
                    } else if (sourceMode == StickerStudioSource.PHOTO && photoUri == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clickable {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = null,
                                tint = colors.accentGreen,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Tap to choose photo",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        previewBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Sticker preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Controls per mode
                if (sourceMode == StickerStudioSource.PHOTO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Image, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (photoUri == null) "Choose Photo" else "Change Photo", fontSize = 12.5.sp)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StickerCutShape.entries.forEach { shape ->
                            FilterChip(
                                selected = cutShape == shape,
                                onClick = { cutShape = shape },
                                label = { Text(shape.label, fontSize = 12.sp) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, null, tint = colors.accentGreen, modifier = Modifier.size(18.dp))
                            Column {
                                Text("Remove Background", color = colors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                                Text("Edge color cutout (works best on solid BGs)", color = colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = removeBg,
                            onCheckedChange = { removeBg = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                        )
                    }
                    if (removeBg) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Sensitivity", color = colors.textSecondary, fontSize = 12.sp)
                            Slider(
                                value = bgTolerance,
                                onValueChange = { bgTolerance = it },
                                valueRange = 18f..110f,
                                colors = SliderDefaults.colors(thumbColor = colors.accentGreen, activeTrackColor = colors.accentGreen),
                                modifier = Modifier.weight(1f)
                            )
                            Text("${bgTolerance.toInt()}", color = colors.textPrimary, fontSize = 12.sp)
                        }
                    }
                }

                if (sourceMode == StickerStudioSource.DRAW) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                                enabled = strokes.isNotEmpty()
                            ) {
                                Icon(Icons.Rounded.Undo, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Undo", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { strokes.clear() },
                                enabled = strokes.isNotEmpty()
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                        Text("Brush: ${brushWidth.toInt()}px", color = colors.textSecondary, fontSize = 12.sp)
                    }
                    Slider(
                        value = brushWidth,
                        onValueChange = { brushWidth = it },
                        valueRange = 6f..38f,
                        colors = SliderDefaults.colors(thumbColor = colors.accentGreen, activeTrackColor = colors.accentGreen)
                    )
                }

                // Shared Outline + Caption + Color Controls
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it.take(32) },
                    singleLine = true,
                    label = {
                        Text(
                            if (sourceMode == StickerStudioSource.TEXT) "Sticker text or big emoji"
                            else "Optional caption text"
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.divider,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ink:", color = colors.textSecondary, fontSize = 12.sp)
                    palette.forEach { argb ->
                        val selected = selectedColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) colors.brightGreen else colors.divider,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorArgb = argb }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("White Sticker Die-Cut Border", color = colors.textPrimary, fontSize = 13.5.sp)
                    Switch(
                        checked = addOutline,
                        onCheckedChange = { addOutline = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                    )
                }

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val finalBmp = withContext(Dispatchers.Default) {
                                    renderStudioStickerBitmap(
                                        sourceMode = sourceMode,
                                        photoBitmap = sourcePhotoBitmap,
                                        cutShape = cutShape,
                                        removeBg = removeBg,
                                        bgTolerance = bgTolerance.toInt(),
                                        addOutline = addOutline,
                                        captionText = captionText,
                                        captionColorArgb = selectedColorArgb,
                                        strokes = strokes.toList(),
                                        drawCanvasPx = drawCanvasSize
                                    )
                                }
                                onSaveToPack(finalBmp, captionText.ifBlank { "Studio Sticker" })
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save to Pack")
                    }
                    if (canSendNow && onSaveAndSend != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val finalBmp = withContext(Dispatchers.Default) {
                                        renderStudioStickerBitmap(
                                            sourceMode = sourceMode,
                                            photoBitmap = sourcePhotoBitmap,
                                            cutShape = cutShape,
                                            removeBg = removeBg,
                                            bgTolerance = bgTolerance.toInt(),
                                            addOutline = addOutline,
                                            captionText = captionText,
                                            captionColorArgb = selectedColorArgb,
                                            strokes = strokes.toList(),
                                            drawCanvasPx = drawCanvasSize
                                        )
                                    }
                                    onSaveAndSend(finalBmp, captionText.ifBlank { "Studio Sticker" })
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save & Send", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
