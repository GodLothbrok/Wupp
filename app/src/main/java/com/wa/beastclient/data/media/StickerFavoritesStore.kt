package com.wa.beastclient.data.media

import android.content.Context
import android.content.SharedPreferences
import com.wa.beastclient.domain.accounts.AccountManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages locally favorited stickers with persistent SharedPreferences storage scoped per active account.
 * Keys are matched against message SHA-256 hashes, local file paths, or message IDs.
 */
class StickerFavoritesStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences
        get() = AccountManager.getScopedPreferences(appContext, "wupp_sticker_favorites")

    private val _favorites = MutableStateFlow<Set<String>>(loadFavorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    fun reloadForActiveAccount() {
        _favorites.value = loadFavorites()
    }

    private fun loadFavorites(): Set<String> {
        val raw = prefs.getStringSet(KEY_FAVORITES, null) ?: emptySet()
        return raw.toSet()
    }

    fun isFavorite(key: String): Boolean {
        if (key.isBlank()) return false
        return _favorites.value.contains(key)
    }

    fun toggleFavorite(key: String): Boolean {
        if (key.isBlank()) return false
        val current = _favorites.value.toMutableSet()
        val isNowFavorite = if (current.contains(key)) {
            current.remove(key)
            false
        } else {
            current.add(key)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        _favorites.value = current
        return isNowFavorite
    }

    fun addFavorite(key: String) {
        if (key.isBlank()) return
        val current = _favorites.value.toMutableSet()
        if (current.add(key)) {
            prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
            _favorites.value = current
        }
    }

    fun removeFavorite(key: String) {
        if (key.isBlank()) return
        val current = _favorites.value.toMutableSet()
        if (current.remove(key)) {
            prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
            _favorites.value = current
        }
    }

    companion object {
        private const val KEY_FAVORITES = "favorite_sticker_keys"

        @Volatile
        private var instance: StickerFavoritesStore? = null

        fun getInstance(context: Context): StickerFavoritesStore {
            return instance ?: synchronized(this) {
                instance ?: StickerFavoritesStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
