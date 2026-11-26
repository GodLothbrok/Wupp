package com.wa.beastclient.domain.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.data.db.WaDao
import com.wa.beastclient.domain.accounts.AccountManager
import com.wa.beastclient.domain.stealth.StealthMatrixManager

internal class DeviceContactIndex private constructor(
    private val exactDigits: Map<String, String>,
    private val suffix10: Map<String, String?>,
    private val suffix9: Map<String, String?>
) {
    fun lookup(vararg candidates: String): String? {
        for (raw in candidates) {
            if (raw.contains("13135550002") || raw.startsWith("meta-ai", ignoreCase = true)) {
                return "Meta AI"
            }
        }
        for (raw in candidates) {
            if (raw.isBlank()) continue
            val bare = raw.trim().substringBefore("@").substringBefore(":")
            if (bare.endsWith("lid", ignoreCase = true) || raw.contains("@lid")) continue
            val digits = bare.filter { it.isDigit() }
            if (digits.length < 6) continue
            exactDigits[digits]?.let { return it }
            val noZero = digits.trimStart('0')
            if (noZero.length >= 6) {
                exactDigits[noZero]?.let { return it }
            }
        }
        for (raw in candidates) {
            if (raw.isBlank() || raw.contains("@lid")) continue
            val digits = raw.trim().substringBefore("@").substringBefore(":").filter { it.isDigit() }
            if (digits.length >= 10) {
                suffix10[digits.takeLast(10)]?.let { return it }
            }
        }
        for (raw in candidates) {
            if (raw.isBlank() || raw.contains("@lid")) continue
            val digits = raw.trim().substringBefore("@").substringBefore(":").filter { it.isDigit() }
            if (digits.length >= 9) {
                suffix9[digits.takeLast(9)]?.let { return it }
            }
        }
        return null
    }

    val isEmpty: Boolean
        get() = exactDigits.isEmpty()

    companion object {
        val EMPTY = DeviceContactIndex(emptyMap(), emptyMap(), emptyMap())

        fun fromEntries(entries: List<Pair<String, String>>): DeviceContactIndex {
            if (entries.isEmpty()) return EMPTY
            val exact = HashMap<String, String>(entries.size * 2)
            val s10 = HashMap<String, String?>(entries.size)
            val s9 = HashMap<String, String?>(entries.size)

            fun putUnique(map: MutableMap<String, String?>, key: String, name: String) {
                if (!map.containsKey(key)) {
                    map[key] = name
                } else {
                    val prev = map[key]
                    if (prev != null && prev != name) {
                        map[key] = null
                    }
                }
            }

            for ((rawPhone, rawName) in entries) {
                val cleanName = rawName.trim()
                if (cleanName.isBlank()) continue
                val digits = rawPhone.filter { it.isDigit() }
                if (digits.length < 6) continue

                exact.putIfAbsent(digits, cleanName)
                val noZero = digits.trimStart('0')
                if (noZero.length >= 6) {
                    exact.putIfAbsent(noZero, cleanName)
                }
                if (digits.length >= 10) {
                    putUnique(s10, digits.takeLast(10), cleanName)
                }
                if (digits.length >= 9) {
                    putUnique(s9, digits.takeLast(9), cleanName)
                }
            }
            return DeviceContactIndex(exact, s10, s9)
        }
    }
}

/**
 * Reads local Android contacts ONLY as a privacy-preserving fallback when WhatsApp servers
 * have not supplied a name for a specific contact or group participant.
 */
object DeviceContactFallbackResolver {
    private const val CACHE_TTL_MS = 60_000L

    @Volatile
    private var cachedIndex: DeviceContactIndex = DeviceContactIndex.EMPTY

    @Volatile
    private var cachedAtMs: Long = 0L

    fun hasReadContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns true when [candidate] does not contain a real display name from WhatsApp servers
     * (e.g. blank, raw JID, numeric user part, or `+phone` string).
     */
    internal fun isServerNameMissing(
        candidate: String?,
        jid: String,
        altJid: String = ""
    ): Boolean {
        val clean = candidate?.trim().orEmpty()
        if (clean.isBlank()) return true
        // Punctuation/dot-only pushnames (e.g. ".", "...", "-", "~") carry no real name
        if (!clean.any { it.isLetterOrDigit() }) return true
        val cleanJid = jid.trim()
        val cleanAlt = altJid.trim()
        val bare = cleanJid.substringBefore("@").substringBefore(":")
        val altBare = cleanAlt.substringBefore("@").substringBefore(":")

        if (clean == cleanJid || (cleanAlt.isNotBlank() && clean == cleanAlt)) return true
        if (bare.isNotBlank() && (clean == bare || clean == "+$bare")) return true
        if (altBare.isNotBlank() && (clean == altBare || clean == "+$altBare")) return true
        if (clean == "Participant" || clean.startsWith("~ User")) return true
        if (clean.startsWith("+") && clean.any { it.isDigit() }) return true
        val looksLikePhoneOnly = clean.any { it.isDigit() } &&
            clean.all { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' }
        return looksLikePhoneOnly
    }

    internal fun isUsableDisplayName(
        candidate: String?,
        jid: String,
        altJid: String = ""
    ): Boolean = !isServerNameMissing(candidate, jid, altJid)

    /**
     * Returns true when [candidate] is actually a group/community JID or legacy `phone-epoch` ID
     * that must never be displayed as an individual message sender name.
     */
    internal fun isGroupJidLikeIdentifier(
        candidate: String?,
        chatJid: String = ""
    ): Boolean {
        val clean = candidate?.trim().orEmpty()
        if (clean.isBlank()) return false
        if (clean.endsWith("@g.us", ignoreCase = true)) return true
        val cleanChat = chatJid.trim()
        val chatBare = cleanChat.substringBefore("@").substringBefore(":")
        if (cleanChat.isNotBlank() && (clean == cleanChat || (chatBare.isNotBlank() && clean == chatBare))) {
            return true
        }
        val bare = clean.substringBefore("@").substringBefore(":")
        val dashIdx = bare.indexOf('-')
        if (dashIdx in 6 until bare.length - 5) {
            val left = bare.substring(0, dashIdx)
            val right = bare.substring(dashIdx + 1)
            if (left.all { it.isDigit() } && right.all { it.isDigit() }) {
                return true
            }
        }
        return false
    }

    internal fun formatIntlPhoneCompact(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 7) return ""
        return when {
            digits.startsWith("92") && digits.length == 12 ->
                "+92 ${digits.substring(2, 5)} ${digits.substring(5)}"
            digits.startsWith("1") && digits.length == 11 ->
                "+1 (${digits.substring(1, 4)}) ${digits.substring(4, 7)}-${digits.substring(7)}"
            digits.length >= 11 ->
                "+${digits.substring(0, 2)} ${digits.substring(2, 5)} ${digits.substring(5)}"
            else -> "+$digits"
        }
    }

    private const val BACKUP_PREFS_NAME = "wa_local_contact_backup"
    private const val KEY_APPLIED_JIDS = "applied_local_contact_jids"
    private const val KEY_SERVER_BACKUP_PREFIX = "srv_"

    fun clearCache() {
        cachedIndex = DeviceContactIndex.EMPTY
        cachedAtMs = 0L
    }

    internal fun clearCacheForTest() {
        clearCache()
    }

    fun isFallbackEnabled(): Boolean =
        StealthMatrixManager.config.value.localContactFallbackEnabled

    fun isPreferPhonebookEnabled(): Boolean =
        StealthMatrixManager.config.value.let {
            it.localContactFallbackEnabled && it.preferPhonebookContactNames
        }

    fun recordServerNameBackup(context: Context, jid: String, serverName: String) {
        val cleanJid = jid.trim()
        val cleanName = serverName.trim()
        if (cleanJid.isBlank() || isServerNameMissing(cleanName, cleanJid)) return
        val prefs = AccountManager.getScopedPreferences(context, BACKUP_PREFS_NAME)
        val key = KEY_SERVER_BACKUP_PREFIX + cleanJid
        if (prefs.getString(key, null) != cleanName) {
            prefs.edit().putString(key, cleanName).apply()
        }
    }

    private fun getOrLoadIndex(context: Context, forceRefresh: Boolean = false): DeviceContactIndex {
        if (!hasReadContactsPermission(context)) {
            clearCache()
            return DeviceContactIndex.EMPTY
        }
        val now = SystemClock.elapsedRealtime()
        if (!forceRefresh && cachedAtMs > 0L && now - cachedAtMs < CACHE_TTL_MS) {
            return cachedIndex
        }
        val rows = ArrayList<Pair<String, String>>()
        runCatching {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                if (numIdx >= 0 && nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val num = cursor.getString(numIdx).orEmpty()
                        val name = cursor.getString(nameIdx).orEmpty()
                        if (num.isNotBlank() && name.isNotBlank()) {
                            rows.add(num to name)
                        }
                    }
                }
            }
        }
        val built = DeviceContactIndex.fromEntries(rows)
        cachedIndex = built
        cachedAtMs = now
        return built
    }

    fun lookupForParticipant(
        context: Context,
        jid: String,
        altJid: String = "",
        phone: String = "",
        forceRefresh: Boolean = false
    ): String? {
        if (!isFallbackEnabled()) return null
        val index = getOrLoadIndex(context, forceRefresh = forceRefresh)
        if (index.isEmpty) return null
        return index.lookup(phone, jid, altJid)
    }

    /**
     * Returns true if [jid] (or [altJid]) matches an entry saved in the device phonebook
     * when READ_CONTACTS permission is granted.
     */
    fun hasSavedPhonebookContact(
        context: Context,
        jid: String,
        altJid: String = ""
    ): Boolean {
        if (!hasReadContactsPermission(context)) return false
        val index = getOrLoadIndex(context, forceRefresh = false)
        if (index.isEmpty) return false
        return !index.lookup("", jid, altJid).isNullOrBlank()
    }

    suspend fun restoreServerNames(
        context: Context,
        dao: WaDao,
        restoreFallbackOnlyJids: Boolean
    ): Int {
        val prefs = AccountManager.getScopedPreferences(context, BACKUP_PREFS_NAME)
        val appliedSet = prefs.getStringSet(KEY_APPLIED_JIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val allEntries = prefs.all
        val candidateJids = HashSet<String>(appliedSet)
        for (k in allEntries.keys) {
            if (k.startsWith(KEY_SERVER_BACKUP_PREFIX)) {
                candidateJids.add(k.removePrefix(KEY_SERVER_BACKUP_PREFIX))
            }
        }
        if (candidateJids.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val contactUpdates = ArrayList<ContactEntity>()
        var restoredCount = 0
        val editor = prefs.edit()

        for (jid in candidateJids) {
            val backupName = prefs.getString(KEY_SERVER_BACKUP_PREFIX + jid, null)?.trim().orEmpty()
            val chat = dao.getChat(jid)
            val contact = dao.getContact(jid)
            if (backupName.isNotBlank()) {
                if (chat != null && chat.name != backupName) {
                    dao.updateChatName(jid, backupName, overwrite = true)
                    restoredCount++
                }
                if (contact != null && contact.name != backupName) {
                    contactUpdates.add(contact.copy(name = backupName, updatedAt = now))
                }
                appliedSet.remove(jid)
            } else if (restoreFallbackOnlyJids && appliedSet.contains(jid)) {
                val bare = jid.substringBefore("@").substringBefore(":")
                val fallbackDisplay = formatIntlPhoneCompact(bare).ifBlank { bare }
                if (chat != null && chat.name != fallbackDisplay) {
                    dao.updateChatName(jid, fallbackDisplay, overwrite = true)
                    restoredCount++
                }
                if (contact != null && contact.name != fallbackDisplay) {
                    contactUpdates.add(contact.copy(name = fallbackDisplay, isSaved = false, updatedAt = now))
                }
                appliedSet.remove(jid)
            }
        }
        if (contactUpdates.isNotEmpty()) {
            dao.upsertContacts(contactUpdates)
        }
        editor.putStringSet(KEY_APPLIED_JIDS, appliedSet).apply()
        return restoredCount
    }

    suspend fun backfillMissingContactNames(
        context: Context,
        dao: WaDao,
        forceRefresh: Boolean = false
    ): Int {
        if (!isFallbackEnabled()) {
            clearCache()
            return 0
        }
        val index = getOrLoadIndex(context, forceRefresh = forceRefresh)
        if (index.isEmpty) return 0

        val preferPhonebook = isPreferPhonebookEnabled()
        val prefs = AccountManager.getScopedPreferences(context, BACKUP_PREFS_NAME)
        val appliedSet = prefs.getStringSet(KEY_APPLIED_JIDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val editor = prefs.edit()

        val chats = dao.getAllChatsOnce()
        val contacts = dao.getAllContactsOnce()
        val contactsByJid = contacts.associateBy { it.jid }
        val contactUpdates = LinkedHashMap<String, ContactEntity>()
        var updatedCount = 0
        val now = System.currentTimeMillis()

        for (chat in chats) {
            if (chat.isGroup || chat.isCommunityParent || chat.jid.endsWith("@newsletter") || chat.jid.endsWith("@broadcast")) {
                continue
            }
            if (chat.jid.contains("13135550002")) {
                if (chat.name != "Meta AI") {
                    dao.updateChatName(chat.jid, "Meta AI", overwrite = true)
                    updatedCount++
                }
                continue
            }
            val existingContact = contactsByJid[chat.jid]
            val localName = index.lookup(chat.jid, chat.name)
            val missingChatName = isServerNameMissing(chat.name, chat.jid)

            if (preferPhonebook) {
                if (!localName.isNullOrBlank()) {
                    if (!missingChatName && !appliedSet.contains(chat.jid)) {
                        editor.putString(KEY_SERVER_BACKUP_PREFIX + chat.jid, chat.name.trim())
                    } else if (existingContact != null && !isServerNameMissing(existingContact.name, chat.jid) && !appliedSet.contains(chat.jid)) {
                        editor.putString(KEY_SERVER_BACKUP_PREFIX + chat.jid, existingContact.name.trim())
                    }
                    appliedSet.add(chat.jid)
                    if (chat.name != localName) {
                        dao.updateChatName(chat.jid, localName, overwrite = true)
                        updatedCount++
                    }
                    if (existingContact == null || existingContact.name != localName) {
                        contactUpdates[chat.jid] = ContactEntity(
                            jid = chat.jid,
                            name = localName,
                            aboutText = existingContact?.aboutText?.ifBlank { chat.aboutText } ?: chat.aboutText,
                            avatarPath = existingContact?.avatarPath ?: chat.avatarPath,
                            isSaved = true,
                            updatedAt = maxOf(chat.lastMessageTimestamp, now)
                        )
                    }
                }
                continue
            }

            if (!missingChatName) continue
            if (existingContact != null && !isServerNameMissing(existingContact.name, chat.jid)) {
                dao.updateChatName(chat.jid, existingContact.name, overwrite = true)
                updatedCount++
                continue
            }
            if (localName.isNullOrBlank()) continue
            appliedSet.add(chat.jid)
            dao.updateChatName(chat.jid, localName, overwrite = true)
            contactUpdates[chat.jid] = ContactEntity(
                jid = chat.jid,
                name = localName,
                aboutText = chat.aboutText,
                avatarPath = chat.avatarPath,
                isSaved = true,
                updatedAt = maxOf(chat.lastMessageTimestamp, now)
            )
            updatedCount++
        }

        // Also update ContactEntity rows (for New Chat / Calls contact pickers)
        for (contact in contacts) {
            if (contact.jid.endsWith("@g.us") || contact.jid.endsWith("@newsletter") || contact.jid.endsWith("@broadcast")) {
                continue
            }
            if (contactUpdates.containsKey(contact.jid)) continue
            val missingName = isServerNameMissing(contact.name, contact.jid)
            if (!preferPhonebook && !missingName) continue
            val localName = index.lookup(contact.jid, contact.name) ?: continue
            if (preferPhonebook && !missingName && !appliedSet.contains(contact.jid)) {
                editor.putString(KEY_SERVER_BACKUP_PREFIX + contact.jid, contact.name.trim())
            }
            appliedSet.add(contact.jid)
            if (contact.name != localName) {
                contactUpdates[contact.jid] = contact.copy(
                    name = localName,
                    isSaved = true,
                    updatedAt = now
                )
                updatedCount++
            }
        }

        editor.putStringSet(KEY_APPLIED_JIDS, appliedSet).apply()
        if (contactUpdates.isNotEmpty()) {
            dao.upsertContacts(contactUpdates.values.toList())
        }
        return updatedCount
    }
}
