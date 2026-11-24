package com.wa.beastclient.ui.components

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.ui.theme.WaColors
import java.io.File
import java.io.FileOutputStream

data class SharedContactPreview(
    val displayName: String,
    val phoneNumber: String,
    val phoneTypeLabel: String = "Mobile",
    val allPhones: List<Pair<String, String>> = emptyList(),
    val vcfFile: File
)

object ContactSharingHelper {

    fun processPickedContact(context: Context, contactUri: Uri): SharedContactPreview? {
        val cr = context.contentResolver
        var displayName: String? = null
        var primaryPhone: String? = null
        var phoneTypeLabel = "Mobile"
        val allPhones = mutableListOf<Pair<String, String>>()
        var lookupKey: String? = null
        var contactId: Long? = null

        val uriStr = contactUri.toString()

        // Path A: User picked directly from CommonDataKinds.Phone (ACTION_PICK with Phone.CONTENT_URI)
        // This grants direct URI read permission for the selected phone number on ALL Android versions
        if (uriStr.contains("data") || uriStr.contains("phone")) {
            runCatching {
                cr.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
                    ),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx)

                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIdx >= 0) primaryPhone = cursor.getString(numIdx)

                        val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                        val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                        if (typeIdx >= 0) {
                            val typeVal = cursor.getInt(typeIdx)
                            val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                            phoneTypeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                context.resources,
                                typeVal,
                                customLabel
                            ).toString()
                        }

                        val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        if (idIdx >= 0) contactId = cursor.getLong(idIdx)

                        val keyIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                        if (keyIdx >= 0) lookupKey = cursor.getString(keyIdx)
                    }
                }
            }
        }

        // Path B: General contact URI (e.g. Contacts.CONTENT_URI) or displayName still missing
        if (displayName.isNullOrBlank() || primaryPhone.isNullOrBlank()) {
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
                        if (idIdx >= 0 && contactId == null) contactId = cursor.getLong(idIdx)

                        val keyIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                        if (keyIdx >= 0 && lookupKey == null) lookupKey = cursor.getString(keyIdx)

                        val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                        if (nameIdx >= 0 && displayName.isNullOrBlank()) {
                            displayName = cursor.getString(nameIdx)
                        }
                    }
                }
            }
        }

        // Path C: If we have contactId, try querying all phone numbers for this contact
        val cid = contactId
        if (cid != null) {
            runCatching {
                cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(cid.toString()),
                    null
                )?.use { cursor ->
                    val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                    while (cursor.moveToNext()) {
                        if (numIdx >= 0) {
                            val num = cursor.getString(numIdx)
                            if (!num.isNullOrBlank()) {
                                val typeVal = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                                val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                                val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                    context.resources,
                                    typeVal,
                                    customLabel
                                ).toString()
                                allPhones.add(num to label)
                                if (primaryPhone.isNullOrBlank()) {
                                    primaryPhone = num
                                    phoneTypeLabel = label
                                }
                            }
                        }
                    }
                }
            }
        }

        // If primaryPhone is found but not in allPhones, add it
        primaryPhone?.let { p ->
            if (allPhones.none { it.first == p }) {
                allPhones.add(0, p to phoneTypeLabel)
            }
        }

        val effectiveName = displayName?.trim()?.ifBlank { null } ?: "Contact"
        val effectivePhone = primaryPhone?.trim()?.ifBlank { null } ?: ""

        // Try reading system vCard stream if lookupKey is present
        var vcardText: String? = null
        if (!lookupKey.isNullOrBlank()) {
            val vcardUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
            runCatching {
                cr.openInputStream(vcardUri)?.use { input ->
                    val text = input.bufferedReader(Charsets.UTF_8).readText()
                    if (text.contains("TEL", ignoreCase = true)) {
                        vcardText = text
                    }
                }
            }
        }

        // Fallback / standard generator: ensure TEL is included
        if (vcardText.isNullOrBlank()) {
            vcardText = buildVCard(
                effectiveName,
                if (allPhones.isNotEmpty()) allPhones else if (effectivePhone.isNotBlank()) listOf(effectivePhone to phoneTypeLabel) else emptyList()
            )
        }

        // Write to cache .vcf file
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

        return SharedContactPreview(
            displayName = effectiveName,
            phoneNumber = effectivePhone,
            phoneTypeLabel = phoneTypeLabel,
            allPhones = allPhones,
            vcfFile = vcfFile
        )
    }

    fun buildVCard(displayName: String, phones: List<Pair<String, String>>): String {
        return buildString {
            appendLine("BEGIN:VCARD")
            appendLine("VERSION:3.0")
            appendLine("FN:$displayName")
            val parts = displayName.trim().split(" ")
            if (parts.size >= 2) {
                val last = parts.last()
                val first = parts.dropLast(1).joinToString(" ")
                appendLine("N:$last;$first;;;")
            } else {
                appendLine("N:$displayName;;;;")
            }
            for ((number, type) in phones) {
                if (number.isNotBlank()) {
                    val cleanType = when {
                        type.contains("home", ignoreCase = true) -> "HOME"
                        type.contains("work", ignoreCase = true) -> "WORK"
                        else -> "CELL"
                    }
                    appendLine("TEL;TYPE=$cleanType:$number")
                }
            }
            appendLine("END:VCARD")
        }
    }
}

/**
 * Clean confirmation dialog shown before sending a contact into the chat,
 * allowing the user to review the contact name and phone number (and select which number if multiple).
 */
@Composable
fun SendContactPreviewDialog(
    contact: SharedContactPreview,
    colors: WaColors,
    onDismiss: () -> Unit,
    onSend: (SharedContactPreview) -> Unit
) {
    var selectedPhone by remember(contact) {
        mutableStateOf(contact.phoneNumber)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        title = {
            Text(
                text = "Share Contact",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large Avatar
                Surface(
                    shape = CircleShape,
                    color = colors.accentGreen.copy(alpha = 0.15f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contact Name
                Text(
                    text = contact.displayName,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Phone number(s)
                if (contact.allPhones.size > 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Select phone number:",
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        contact.allPhones.forEach { (number, label) ->
                            val isSelected = selectedPhone == number
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) colors.accentGreen.copy(alpha = 0.12f) else colors.appSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPhone = number }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Phone,
                                        contentDescription = null,
                                        tint = if (isSelected) colors.accentGreen else colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = number,
                                            color = colors.textPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = label,
                                            color = colors.textSecondary,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (contact.phoneNumber.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.appSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = colors.accentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.phoneNumber,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = contact.phoneTypeLabel,
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No phone number found for this contact",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalContact = if (selectedPhone != contact.phoneNumber) {
                        contact.copy(phoneNumber = selectedPhone)
                    } else {
                        contact
                    }
                    onSend(finalContact)
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Send Contact",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Cancel",
                    color = colors.textSecondary
                )
            }
        }
    )
}
