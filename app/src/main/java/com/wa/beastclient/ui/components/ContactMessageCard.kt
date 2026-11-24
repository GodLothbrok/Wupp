package com.wa.beastclient.ui.components

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.theme.WaColors
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height

/**
 * Returns true when this message is a shared contact attachment (vCard).
 */
internal fun isContactMessage(msg: MessageEntity): Boolean {
    if (msg.isDeletedBySender) return false
    val upper = msg.msgType.uppercase()
    if (upper == "CONTACT") return true
    val mime = msg.mimeType.lowercase()
    if (mime == "text/vcard" || mime == "text/x-vcard") return true
    val body = msg.body.trim()
    if (body.endsWith(".vcf", ignoreCase = true)) return true
    return false
}

/**
 * Extracts a phone number from an attached vCard file or body text if present.
 */
internal fun extractPhoneFromVCardOrMessage(msg: MessageEntity): String? {
    val path = msg.localMediaPath
    if (!path.isNullOrBlank()) {
        val file = File(path)
        if (file.exists() && file.length() > 0L) {
            val phone = runCatching {
                file.useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.startsWith("TEL", ignoreCase = true) && trimmed.contains(":")) {
                            return@runCatching trimmed.substringAfter(":").trim()
                        }
                    }
                    null
                }
            }.getOrNull()
            if (!phone.isNullOrBlank()) return phone
        }
    }
    return null
}

/**
 * Resolves contact display name and phone number from message body and attached vCard.
 */
internal fun resolveContactNameAndPhone(msg: MessageEntity): Pair<String, String?> {
    var displayName = "Contact"
    var phoneNumber: String? = null

    val body = msg.body.trim()
    if (body.contains("BEGIN:VCARD", ignoreCase = true)) {
        for (line in body.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("FN:", ignoreCase = true)) {
                displayName = trimmed.substringAfter(":").trim()
            } else if (displayName == "Contact" && trimmed.startsWith("N:", ignoreCase = true)) {
                val parts = trimmed.substringAfter(":").split(";").filter { it.isNotBlank() }
                if (parts.isNotEmpty()) displayName = parts.reversed().joinToString(" ")
            } else if (trimmed.startsWith("TEL", ignoreCase = true) && trimmed.contains(":")) {
                val num = trimmed.substringAfter(":").trim()
                if (phoneNumber.isNullOrBlank() && num.isNotBlank()) {
                    phoneNumber = num
                }
            }
        }
    } else if (body.contains('\n')) {
        val firstLine = body.substringBefore('\n').trim()
        val secondLine = body.substringAfter('\n').trim()
        if (firstLine.isNotBlank()) displayName = firstLine
        if (secondLine.isNotBlank() && (secondLine.contains('+') || secondLine.any { it.isDigit() })) {
            phoneNumber = secondLine
        }
    } else if (body.isNotBlank()) {
        displayName = body.removeSuffix(".vcf").removeSuffix(".VCF").trim()
    }

    if (phoneNumber.isNullOrBlank()) {
        phoneNumber = extractPhoneFromVCardOrMessage(msg)
    }

    return displayName.ifBlank { "Contact" } to phoneNumber
}

/**
 * Renders a contact attachment as a native WhatsApp-style card: avatar placeholder, display name,
 * phone number, a divider, and action buttons ("Message" and "Add to contacts").
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactMessageCard(
    msg: MessageEntity,
    colors: WaColors,
    onLongClick: () -> Unit,
    onMessageClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val (displayName, phoneNumber) = remember(msg.body, msg.localMediaPath) {
        resolveContactNameAndPhone(msg)
    }

    val openAddContact = {
        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, displayName)
            if (!phoneNumber.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            }
        }
        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!phoneNumber.isNullOrBlank()) onMessageClick(phoneNumber)
                    else openAddContact()
                },
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Avatar circle with person icon
            Surface(
                shape = CircleShape,
                color = colors.textSecondary.copy(alpha = 0.18f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(28.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = phoneNumber?.ifBlank { null } ?: "Contact",
                    color = if (phoneNumber.isNullOrBlank()) colors.textSecondary else colors.textPrimary.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.15f))

        if (!phoneNumber.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onMessageClick(phoneNumber) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Message",
                        color = colors.accentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(colors.textSecondary.copy(alpha = 0.2f))
                )

                TextButton(
                    onClick = { openAddContact() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Add to contacts",
                        color = colors.accentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            TextButton(
                onClick = { openAddContact() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "Add to contacts",
                    color = colors.accentGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
