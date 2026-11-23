package com.wa.beastclient.domain.accounts

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class WuppAccount(
    val id: String,
    val label: String,
    val ownJid: String = "",
    val pushName: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastActiveAtMs: Long = System.currentTimeMillis()
) {
    val displayPhone: String
        get() {
            val user = ownJid.trim().substringBefore("@").substringBefore(":")
            return if (user.isNotBlank() && user.all { it.isDigit() }) "+$user" else ""
        }

    val displayTitle: String
        get() = label.ifBlank {
            pushName.ifBlank { displayPhone.ifBlank { "Account" } }
        }
}

/**
 * Manages isolated single-active-account storage roots (`filesDir/accounts/<id>/` and per-account
 * Room DB / SharedPreferences) while preserving `DEFAULT_ACCOUNT_ID` on legacy root paths so
 * existing single-account installs keep all chats and linked sessions without migration risk.
 */
object AccountManager {
    const val DEFAULT_ACCOUNT_ID = "default"
    private const val REGISTRY_PREFS = "wupp_accounts_registry"
    private const val KEY_ACCOUNTS_JSON = "accounts_json"
    private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"

    private val ACCOUNT_PREF_BASES = listOf(
        "wupp_stealth_matrix",
        "wupp_sticker_favorites",
        "wupp_migrations",
        "wupp_gallery_exported",
        "wupp_jid_aliases",
        "wupp_group_locks"
    )

    private val _accounts = MutableStateFlow(listOf(defaultAccount()))
    val accounts: StateFlow<List<WuppAccount>> = _accounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow(DEFAULT_ACCOUNT_ID)
    val activeAccountId: StateFlow<String> = _activeAccountId.asStateFlow()

    private val _accountGeneration = MutableStateFlow(0L)
    val accountGeneration: StateFlow<Long> = _accountGeneration.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val prefs = registryPrefs(context)
            val loaded = decodeAccountsJson(prefs.getString(KEY_ACCOUNTS_JSON, null))
            val ensured = if (loaded.isEmpty()) listOf(defaultAccount()) else loaded
            val storedActive = sanitizeAccountId(prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null).orEmpty())
            val resolvedActive = if (ensured.any { it.id == storedActive }) storedActive else ensured.first().id
            _accounts.value = ensured
            _activeAccountId.value = resolvedActive
            persistLocked(prefs, ensured, resolvedActive)
            initialized = true
        }
    }

    fun getActiveAccount(): WuppAccount {
        val curId = _activeAccountId.value
        return _accounts.value.firstOrNull { it.id == curId } ?: _accounts.value.first()
    }

    fun dataDir(context: Context, accountId: String = _activeAccountId.value): File {
        val cleanId = sanitizeAccountId(accountId)
        val root = context.applicationContext.filesDir
        return if (cleanId == DEFAULT_ACCOUNT_ID) {
            root
        } else {
            File(root, "accounts/$cleanId").apply { if (!exists()) mkdirs() }
        }
    }

    fun roomDatabaseFile(context: Context, accountId: String = _activeAccountId.value): File {
        val cleanId = sanitizeAccountId(accountId)
        val baseDir = dataDir(context, cleanId)
        return File(baseDir, "wa_ui.db")
    }

    fun prefsName(baseName: String, accountId: String = _activeAccountId.value): String =
        scopedPrefsName(baseName, accountId)

    fun getScopedPreferences(
        context: Context,
        baseName: String,
        accountId: String = _activeAccountId.value
    ): SharedPreferences = context.applicationContext.getSharedPreferences(
        scopedPrefsName(baseName, accountId),
        Context.MODE_PRIVATE
    )

    fun createAccount(context: Context, label: String = ""): WuppAccount {
        init(context)
        synchronized(this) {
            val existing = _accounts.value.toMutableList()
            val nextNum = existing.size + 1
            var candidateId = "acc_${nextNum}"
            var bump = nextNum
            while (existing.any { it.id == candidateId }) {
                bump += 1
                candidateId = "acc_$bump"
            }
            val now = System.currentTimeMillis()
            val cleanLabel = label.trim().ifBlank { "Account $nextNum" }
            val created = WuppAccount(
                id = candidateId,
                label = cleanLabel,
                createdAtMs = now,
                lastActiveAtMs = now
            )
            dataDir(context, created.id)
            existing += created
            _accounts.value = existing
            persistLocked(registryPrefs(context), existing, _activeAccountId.value)
            return created
        }
    }

    fun renameAccount(context: Context, accountId: String, newLabel: String) {
        init(context)
        val cleanLabel = newLabel.trim()
        if (cleanLabel.isBlank()) return
        synchronized(this) {
            val updated = _accounts.value.map { acc ->
                if (acc.id == accountId) acc.copy(label = cleanLabel) else acc
            }
            _accounts.value = updated
            persistLocked(registryPrefs(context), updated, _activeAccountId.value)
        }
    }

    fun updateActiveIdentity(context: Context, ownJid: String, pushName: String? = null) {
        init(context)
        val cleanJid = ownJid.trim()
        val cleanPush = pushName?.trim().orEmpty()
        if (cleanJid.isBlank() && cleanPush.isBlank()) return
        synchronized(this) {
            val targetId = _activeAccountId.value
            var changed = false
            val updated = _accounts.value.map { acc ->
                if (acc.id != targetId) {
                    acc
                } else {
                    val nextJid = cleanJid.ifBlank { acc.ownJid }
                    val nextPush = cleanPush.ifBlank { acc.pushName }
                    if (nextJid != acc.ownJid || nextPush != acc.pushName) {
                        changed = true
                        acc.copy(ownJid = nextJid, pushName = nextPush)
                    } else {
                        acc
                    }
                }
            }
            if (changed) {
                _accounts.value = updated
                persistLocked(registryPrefs(context), updated, targetId)
            }
        }
    }

    fun clearActiveIdentity(context: Context) {
        init(context)
        synchronized(this) {
            val targetId = _activeAccountId.value
            var changed = false
            val updated = _accounts.value.map { acc ->
                if (acc.id == targetId && (acc.ownJid.isNotBlank() || acc.pushName.isNotBlank())) {
                    changed = true
                    acc.copy(ownJid = "", pushName = "")
                } else {
                    acc
                }
            }
            if (changed) {
                _accounts.value = updated
                persistLocked(registryPrefs(context), updated, targetId)
            }
        }
    }

    fun setActiveAccount(context: Context, targetAccountId: String): Boolean {
        init(context)
        val cleanTarget = sanitizeAccountId(targetAccountId)
        synchronized(this) {
            val current = _accounts.value
            if (current.none { it.id == cleanTarget }) return false
            val now = System.currentTimeMillis()
            val updated = current.map { acc ->
                if (acc.id == cleanTarget) acc.copy(lastActiveAtMs = now) else acc
            }
            _accounts.value = updated
            _activeAccountId.value = cleanTarget
            persistLocked(registryPrefs(context), updated, cleanTarget)
            _accountGeneration.value = _accountGeneration.value + 1L
            return true
        }
    }

    fun bumpGeneration() {
        _accountGeneration.value = _accountGeneration.value + 1L
    }

    fun removeInactiveAccount(context: Context, accountId: String): Boolean {
        init(context)
        val cleanId = sanitizeAccountId(accountId)
        if (cleanId == _activeAccountId.value || cleanId == DEFAULT_ACCOUNT_ID) return false
        synchronized(this) {
            val filtered = _accounts.value.filterNot { it.id == cleanId }
            if (filtered.size == _accounts.value.size || filtered.isEmpty()) return false
            _accounts.value = filtered
            persistLocked(registryPrefs(context), filtered, _activeAccountId.value)
        }
        runCatching {
            val dir = File(context.applicationContext.filesDir, "accounts/$cleanId")
            if (dir.exists()) dir.deleteRecursively()
        }
        ACCOUNT_PREF_BASES.forEach { base ->
            runCatching {
                getScopedPreferences(context, base, cleanId).edit().clear().commit()
            }
        }
        return true
    }

    private fun registryPrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(REGISTRY_PREFS, Context.MODE_PRIVATE)

    private fun persistLocked(
        prefs: SharedPreferences,
        accounts: List<WuppAccount>,
        activeId: String
    ) {
        prefs.edit()
            .putString(KEY_ACCOUNTS_JSON, encodeAccountsJson(accounts))
            .putString(KEY_ACTIVE_ACCOUNT_ID, activeId)
            .apply()
    }

    private fun defaultAccount(): WuppAccount =
        WuppAccount(
            id = DEFAULT_ACCOUNT_ID,
            label = "Primary Account"
        )
}

internal fun sanitizeAccountId(raw: String): String {
    val cleaned = raw.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
    return cleaned.ifBlank { AccountManager.DEFAULT_ACCOUNT_ID }
}

internal fun scopedPrefsName(baseName: String, accountId: String): String {
    val cleanId = sanitizeAccountId(accountId)
    return if (cleanId == AccountManager.DEFAULT_ACCOUNT_ID) {
        baseName
    } else {
        "${baseName}__acct_$cleanId"
    }
}

internal fun encodeAccountsJson(accounts: List<WuppAccount>): String {
    val arr = JSONArray()
    for (acc in accounts) {
        arr.put(
            JSONObject().apply {
                put("id", acc.id)
                put("label", acc.label)
                put("ownJid", acc.ownJid)
                put("pushName", acc.pushName)
                put("createdAtMs", acc.createdAtMs)
                put("lastActiveAtMs", acc.lastActiveAtMs)
            }
        )
    }
    return arr.toString()
}

internal fun decodeAccountsJson(raw: String?): List<WuppAccount> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        val out = ArrayList<WuppAccount>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = sanitizeAccountId(obj.optString("id"))
            if (out.any { it.id == id }) continue
            out += WuppAccount(
                id = id,
                label = obj.optString("label").ifBlank { if (id == AccountManager.DEFAULT_ACCOUNT_ID) "Primary Account" else "Account" },
                ownJid = obj.optString("ownJid"),
                pushName = obj.optString("pushName"),
                createdAtMs = obj.optLong("createdAtMs", 0L),
                lastActiveAtMs = obj.optLong("lastActiveAtMs", 0L)
            )
        }
        out
    }.getOrDefault(emptyList())
}
