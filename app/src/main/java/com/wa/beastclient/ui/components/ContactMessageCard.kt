package com.wa.beastclient.ui.components

import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.ui.theme.WaColors

/**
 * Returns true when this message is a shared contact attachment (vCard).
 */
internal fun isContactMessage(msg: MessageEntity): Boolean =
    msg.msgType.uppercase() == "CONTACT" && !msg.isDeletedBySender

/**
 * Renders a contact attachment as a small card: avatar placeholder, display name,
 * a divider, and an "Add to contacts" action button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactMessageCard(
    msg: MessageEntity,
    colors: WaColors,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val displayName = msg.body.trim().ifBlank { "Unknown Contact" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
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
                    text = "Contact",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.15f))

        TextButton(
            onClick = {
                val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.NAME, displayName)
                }
                runCatching { context.startActivity(intent) }
            },
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
