package com.wa.beastclient.ui.components

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import java.io.File
import java.io.FileOutputStream

data class SharedContactData(
    val displayName: String,
    val phoneNumber: String?,
    val vcardFile: File
)

object ContactSharingHelper {

    fun processPickedContact(context: Context, contactUri: Uri): SharedContactData? {
        val cr = context.contentResolver
        var displayName: String? = null
        var lookupKey: String? = null
        var contactId: Long? = null

        // 1. Query contact basic info (name, lookup key, id)
        runCatching {
            cr.query(
                contactUri,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    if (idIdx >= 0) contactId = cursor.getLong(idIdx)
                    val keyIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    if (keyIdx >= 0) lookupKey = cursor.getString(keyIdx)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                }
            }
        }

        val effectiveName = displayName?.trim()?.ifBlank { null } ?: "Contact"

        // 2. Query phone number
        var phoneNumber: String? = null
        val id = contactId
        if (id != null) {
            runCatching {
                cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(id.toString()),
                    null
                )?.use { phoneCursor ->
                    if (phoneCursor.moveToFirst()) {
                        val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIdx >= 0) phoneNumber = phoneCursor.getString(numIdx)
                    }
                }
            }
        }

        // 3. Try reading Android system vCard stream if lookupKey is present
        var vcardText: String? = null
        if (!lookupKey.isNullOrBlank()) {
            val vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
            runCatching {
                cr.openInputStream(vcardUri)?.use { input ->
                    vcardText = input.bufferedReader(Charsets.UTF_8).readText()
                }
            }
        }

        // 4. Fallback if system vCard wasn't available: generate standard vCard 3.0
        if (vcardText.isNullOrBlank()) {
            vcardText = buildVCard(effectiveName, phoneNumber)
        }

        // 5. Write to cache .vcf file
        val safeName = effectiveName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "contact" }
        val vcfDir = File(context.cacheDir, "shared_contacts").apply { if (!exists()) mkdirs() }
        val vcfFile = File(vcfDir, "$safeName.vcf")
        val writeSuccess = runCatching {
            FileOutputStream(vcfFile).use { out ->
                out.write(vcardText.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)

        if (!writeSuccess || !vcfFile.exists() || vcfFile.length() == 0L) {
            return null
        }

        return SharedContactData(
            displayName = effectiveName,
            phoneNumber = phoneNumber,
            vcardFile = vcfFile
        )
    }

    fun buildVCard(displayName: String, phoneNumber: String?): String {
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$displayName")
            if (!phoneNumber.isNullOrBlank()) {
                appendLine("TEL;TYPE=CELL:$phoneNumber")
            }
            appendLine("END:VCARD")
        }
    }
}
