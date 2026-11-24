package com.wa.beastclient.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.ContactEntity
import com.wa.beastclient.domain.contacts.DeviceContactFallbackResolver
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import com.wa.beastclient.ui.theme.WaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InAppContactItem(
    val displayName: String,
    val phoneNumber: String,
    val phoneTypeLabel: String = "Mobile",
    val jid: String = "",
    val avatarPath: String? = null,
    val allPhones: List<Pair<String, String>> = emptyList()
)

/**
 * Loads contacts directly within Wupp without opening the external OS Contacts app.
 * Aggregates in-app synced contacts, 1:1 chats, and local phonebook contacts (if permission granted).
 */
@Composable
fun InAppContactPickerSheet(
    colors: WaColors,
    contacts: List<ContactEntity>,
    chats: List<ChatEntity>,
    ownJid: String,
    onRequestPermission: () -> Unit = {},
    onDismiss: () -> Unit,
    onContactSelected: (SharedContactPreview) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var allItems by remember { mutableStateOf<List<InAppContactItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val hasReadContacts = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(contacts, chats, ownJid, hasReadContacts) {
        val list = withContext(Dispatchers.IO) {
            val byKey = LinkedHashMap<String, InAppContactItem>()

            // 1. In-app synced contacts
            for (c in contacts) {
                if (c.jid.endsWith("@g.us") || c.jid.endsWith("@newsletter") ||
                    c.jid == "status@broadcast" || c.jid.endsWith("@broadcast")
                ) continue
                if (StealthMatrixManager.isSelfChatJid(c.jid, ownJid)) continue

                val phone = if (c.jid.endsWith("@s.whatsapp.net")) {
                    "+${c.jid.substringBefore("@")}"
                } else {
                    ""
                }
                val name = c.name.trim().ifBlank { phone.ifBlank { "Contact" } }
                val key = phone.filter { it.isDigit() }.ifBlank { c.jid }
                byKey[key] = InAppContactItem(
                    displayName = name,
                    phoneNumber = phone,
                    phoneTypeLabel = "WhatsApp",
                    jid = c.jid,
                    avatarPath = c.avatarPath,
                    allPhones = if (phone.isNotBlank()) listOf(phone to "WhatsApp") else emptyList()
                )
            }

            // 2. In-app 1:1 chats
            for (ch in chats) {
                if (ch.isGroup || ch.isCommunityParent || ch.jid.endsWith("@newsletter") ||
                    ch.jid == "status@broadcast" || ch.jid.endsWith("@broadcast")
                ) continue
                if (StealthMatrixManager.isSelfChatJid(ch.jid, ownJid)) continue

                val phone = if (ch.jid.endsWith("@s.whatsapp.net")) {
                    "+${ch.jid.substringBefore("@")}"
                } else {
                    ""
                }
                val key = phone.filter { it.isDigit() }.ifBlank { ch.jid }
                val existing = byKey[key]
                if (existing == null) {
                    val name = ch.name.trim().ifBlank { phone.ifBlank { "Contact" } }
                    byKey[key] = InAppContactItem(
                        displayName = name,
                        phoneNumber = phone,
                        phoneTypeLabel = "Chat",
                        jid = ch.jid,
                        avatarPath = ch.avatarPath,
                        allPhones = if (phone.isNotBlank()) listOf(phone to "Chat") else emptyList()
                    )
                } else {
                    val realName = ch.name.trim()
                    if (realName.isNotBlank() && !realName.startsWith("+") && existing.displayName.startsWith("+")) {
                        byKey[key] = existing.copy(
                            displayName = realName,
                            avatarPath = ch.avatarPath?.takeIf { it.isNotBlank() } ?: existing.avatarPath
                        )
                    }
                }
            }

            // 3. Device phonebook (if permission granted)
            if (hasReadContacts) {
                runCatching {
                    val projection = arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                    )
                    context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        projection,
                        null,
                        null,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC"
                    )?.use { cursor ->
                        val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                        val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                        val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                        while (cursor.moveToNext()) {
                            val num = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                            val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                            if (num.isBlank() && name.isBlank()) continue

                            val typeVal = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                            val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                            val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                context.resources,
                                typeVal,
                                customLabel
                            ).toString()

                            val digits = num.filter { it.isDigit() }
                            val key = if (digits.length >= 7) digits.takeLast(10) else name
                            val existing = byKey[key]

                            if (existing != null) {
                                val currentPhones = existing.allPhones.toMutableList()
                                if (num.isNotBlank() && currentPhones.none { it.first == num }) {
                                    currentPhones.add(num to label)
                                }
                                val effectiveName = if (name.isNotBlank() && (existing.displayName.isBlank() || existing.displayName.startsWith("+"))) {
                                    name
                                } else {
                                    existing.displayName
                                }
                                byKey[key] = existing.copy(
                                    displayName = effectiveName,
                                    phoneNumber = existing.phoneNumber.ifBlank { num },
                                    phoneTypeLabel = if (existing.phoneTypeLabel == "WhatsApp" || existing.phoneTypeLabel == "Chat") existing.phoneTypeLabel else label,
                                    allPhones = currentPhones
                                )
                            } else {
                                byKey[key] = InAppContactItem(
                                    displayName = name.ifBlank { num },
                                    phoneNumber = num,
                                    phoneTypeLabel = label,
                                    allPhones = if (num.isNotBlank()) listOf(num to label) else emptyList()
                                )
                            }
                        }
                    }
                }
            }

            byKey.values.sortedWith(
                compareBy<InAppContactItem> { it.displayName.lowercase() }
                    .thenBy { it.phoneNumber }
            )
        }
        allItems = list
        isLoading = false
    }

    val filteredContacts = remember(allItems, query) {
        val q = query.trim().lowercase()
        val qDigits = q.filter { it.isDigit() }
        if (q.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                item.displayName.lowercase().contains(q) ||
                    item.phoneNumber.contains(q) ||
                    (qDigits.length >= 3 && item.phoneNumber.filter { it.isDigit() }.contains(qDigits))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Share Contact",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = if (isLoading) "Loading contacts..." else "${filteredContacts.size} contacts",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            text = "Search name or number...",
                            color = colors.textSecondary.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accentGreen,
                        unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.25f),
                        cursorColor = colors.accentGreen,
                        focusedContainerColor = colors.appSurface,
                        unfocusedContainerColor = colors.appSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Optional permission prompt banner if READ_CONTACTS is missing
                if (!hasReadContacts) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.accentGreen.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestPermission() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tap to include phonebook contacts",
                                color = colors.accentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Contact list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    if (filteredContacts.isEmpty() && !isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (query.isBlank()) "No contacts found" else "No matching contacts",
                                    color = colors.textSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    items(filteredContacts, key = { it.jid.ifBlank { "${it.displayName}_${it.phoneNumber}" } }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val phones = if (item.allPhones.isNotEmpty()) {
                                        item.allPhones
                                    } else if (item.phoneNumber.isNotBlank()) {
                                        listOf(item.phoneNumber to item.phoneTypeLabel)
                                    } else {
                                        emptyList()
                                    }
                                    val vcard = ContactSharingHelper.buildVCard(
                                        displayName = item.displayName,
                                        phones = phones
                                    )
                                    onContactSelected(
                                        SharedContactPreview(
                                            displayName = item.displayName,
                                            phoneNumber = item.phoneNumber,
                                            phoneTypeLabel = item.phoneTypeLabel,
                                            allPhones = phones,
                                            vcardText = vcard
                                        )
                                    )
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar circle
                            Surface(
                                shape = CircleShape,
                                color = colors.accentGreen.copy(alpha = 0.16f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val initial = item.displayName.firstOrNull { it.isLetter() }
                                    if (initial != null) {
                                        Text(
                                            text = initial.uppercase(),
                                            color = colors.accentGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = colors.accentGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = if (item.phoneNumber.isNotBlank()) {
                                    if (item.phoneTypeLabel.isNotBlank()) {
                                        "${item.phoneNumber} · ${item.phoneTypeLabel}"
                                    } else {
                                        item.phoneNumber
                                    }
                                } else {
                                    item.phoneTypeLabel
                                }
                                Text(
                                    text = subtitle,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Cancel", color = colors.textSecondary)
            }
        }
    )
}
