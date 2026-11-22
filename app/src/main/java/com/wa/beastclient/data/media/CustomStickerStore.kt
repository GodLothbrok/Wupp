package com.wa.beastclient.data.media

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class CustomStickerItem(
    val id: String,
    val label: String,
    val filePath: String,
    val createdAt: Long
)

/**
 * Local-only store for stickers created in Wupp Sticker Studio.
 * Encodes 512x512 WebP assets with transparency and stores metadata locally with zero network traffic.
 */
class CustomStickerStore private constructor(private val appContext: Context) {
    private val stickersDir: File
        get() = EagerMediaVault.customStickersDir(appContext)
    private val manifestFile: File
        get() = File(stickersDir, "manifest.json")

    private val _stickers = MutableStateFlow<List<CustomStickerItem>>(emptyList())
    val stickers: StateFlow<List<CustomStickerItem>> = _stickers.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val loaded = loadManifest().filter { File(it.filePath).let { f -> f.exists() && f.length() > 0L } }
        val knownPaths = loaded.mapTo(HashSet()) { it.filePath }
        val discovered = stickersDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("webp", ignoreCase = true) && !knownPaths.contains(it.absolutePath) }
            ?.map { f ->
                CustomStickerItem(
                    id = f.nameWithoutExtension,
                    label = "Studio Sticker",
                    filePath = f.absolutePath,
                    createdAt = f.lastModified()
                )
            }
            .orEmpty()
        val merged = (loaded + discovered).sortedByDescending { it.createdAt }
        _stickers.value = merged
    }

    fun saveStickerBitmap(bitmap: Bitmap, label: String = "My Sticker"): Result<CustomStickerItem> =
        runCatching {
            val dir = stickersDir
            if (!dir.exists()) dir.mkdirs()
            val scaled = if (bitmap.width == 512 && bitmap.height == 512) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, 512, 512, true)
            }
            val now = System.currentTimeMillis()
            val id = "studio_$now"
            val outFile = File(dir, "$id.webp")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(outFile).use { out ->
                scaled.compress(format, 90, out)
            }
            if (scaled !== bitmap) scaled.recycle()

            val item = CustomStickerItem(
                id = id,
                label = label.trim().ifBlank { "My Sticker" }.take(32),
                filePath = outFile.absolutePath,
                createdAt = now
            )
            val updated = listOf(item) + _stickers.value.filterNot { it.id == id }
            saveManifest(updated)
            _stickers.value = updated
            item
        }

    fun deleteSticker(id: String): Boolean {
        val target = _stickers.value.firstOrNull { it.id == id } ?: return false
        runCatching { File(target.filePath).delete() }
        val updated = _stickers.value.filterNot { it.id == id }
        saveManifest(updated)
        _stickers.value = updated
        return true
    }

    private fun loadManifest(): List<CustomStickerItem> {
        if (!manifestFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(manifestFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val path = obj.optString("filePath").trim()
                    if (id.isNotBlank() && path.isNotBlank()) {
                        add(
                            CustomStickerItem(
                                id = id,
                                label = obj.optString("label", "My Sticker"),
                                filePath = path,
                                createdAt = obj.optLong("createdAt", 0L)
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveManifest(items: List<CustomStickerItem>) {
        runCatching {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("label", item.label)
                        put("filePath", item.filePath)
                        put("createdAt", item.createdAt)
                    }
                )
            }
            manifestFile.writeText(arr.toString())
        }
    }

    companion object {
        const val PACK_NAME = "My Stickers"

        @Volatile
        private var instance: CustomStickerStore? = null

        fun getInstance(context: Context): CustomStickerStore {
            return instance ?: synchronized(this) {
                instance ?: CustomStickerStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
