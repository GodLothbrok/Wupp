package com.wa.beastclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.domain.scheduling.ScheduledMessage
import com.wa.beastclient.domain.scheduling.ScheduledMessageManager
import com.wa.beastclient.domain.scheduling.ScheduledRepeatType
import com.wa.beastclient.domain.scheduling.ScheduledStatus
import com.wa.beastclient.domain.scheduling.formatRecurrenceLabel
import com.wa.beastclient.domain.scheduling.formatScheduledDateTime
import com.wa.beastclient.ui.theme.WaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesSheet(
    filterChatJid: String? = null,
    filterChatTitle: String = "",
    colors: WaColors,
    onDismiss: () -> Unit,
    onOpenComposer: (chatJid: String, chatTitle: String) -> Unit,
    onEditMessage: (ScheduledMessage) -> Unit,
    onPauseMessage: (String) -> Unit,
    onResumeMessage: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onRetryMessage: (String) -> Unit
) {
    val allMessages by ScheduledMessageManager.messages.collectAsState()
    var onlyThisChat by remember { mutableStateOf(!filterChatJid.isNullOrBlank()) }

    val displayedMessages = remember(allMessages, onlyThisChat, filterChatJid) {
        if (onlyThisChat && !filterChatJid.isNullOrBlank()) {
            allMessages.filter { it.chatJid == filterChatJid }
        } else {
            allMessages
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = colors.accentGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Scheduled Messages",
                        color = colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (displayedMessages.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = colors.accentGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${displayedMessages.size}",
                                color = colors.accentGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        val targetJid = filterChatJid ?: ""
                        val targetTitle = filterChatTitle
                        onOpenComposer(targetJid, targetTitle)
                    }
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp), tint = colors.accentGreen)
                    Spacer(Modifier.width(4.dp))
                    Text("Schedule", color = colors.accentGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            // Chat filter toggle (if opened from a specific chat)
            if (!filterChatJid.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = onlyThisChat,
                        onClick = { onlyThisChat = true },
                        label = { Text("This chat (${filterChatTitle.ifBlank { "Current" }})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = !onlyThisChat,
                        onClick = { onlyThisChat = false },
                        label = { Text("All chats", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            HorizontalDivider(
                color = colors.textSecondary.copy(alpha = 0.12f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Message List or Empty State
            if (displayedMessages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.textSecondary.copy(alpha = 0.1f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .padding(16.dp)
                                .size(32.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No scheduled messages",
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Compose a message and set it to send automatically at any date or recurring interval.",
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val targetJid = filterChatJid ?: ""
                            val targetTitle = filterChatTitle
                            onOpenComposer(targetJid, targetTitle)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Schedule a message", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedMessages, key = { it.id }) { item ->
                        ScheduledMessageCard(
                            item = item,
                            colors = colors,
                            onEdit = { onEditMessage(item) },
                            onTogglePause = {
                                if (item.status == ScheduledStatus.PAUSED) {
                                    onResumeMessage(item.id)
                                } else {
                                    onPauseMessage(item.id)
                                }
                            },
                            onDelete = { onDeleteMessage(item.id) },
                            onRetry = { onRetryMessage(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduledMessageCard(
    item: ScheduledMessage,
    colors: WaColors,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.appSurface.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Chat recipient + Status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.chatTitle.ifBlank { item.chatJid.substringBefore("@") },
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                // Status Badge
                val (badgeBg, badgeText, badgeColor) = when (item.status) {
                    ScheduledStatus.PENDING -> Triple(colors.accentGreen.copy(alpha = 0.15f), "Scheduled", colors.accentGreen)
                    ScheduledStatus.SENT -> Triple(Color(0xFF2196F3).copy(alpha = 0.15f), "Sent", Color(0xFF2196F3))
                    ScheduledStatus.FAILED -> Triple(Color(0xFFF44336).copy(alpha = 0.15f), "Failed", Color(0xFFF44336))
                    ScheduledStatus.PAUSED -> Triple(Color(0xFFFF9800).copy(alpha = 0.15f), "Paused", Color(0xFFFF9800))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Message Body Text
            Text(
                text = item.text,
                color = colors.textPrimary,
                fontSize = 13.5.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.failureReason.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Reason: ${item.failureReason}",
                    color = Color(0xFFF44336),
                    fontSize = 11.5.sp,
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(8.dp))

            // Recurrence and Time Info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (item.repeatType != ScheduledRepeatType.ONCE) Icons.Rounded.EventRepeat else Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${formatScheduledDateTime(item.scheduledEpochMs)} · ${formatRecurrenceLabel(item.repeatType, item.customDaysOfWeek)}",
                    color = colors.textSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Actions: Pause/Resume, Retry, Edit, Delete
                if (item.status == ScheduledStatus.FAILED) {
                    IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Refresh, "Retry", tint = colors.accentGreen, modifier = Modifier.size(16.dp))
                    }
                }

                if (item.status == ScheduledStatus.PENDING || item.status == ScheduledStatus.PAUSED) {
                    IconButton(onClick = onTogglePause, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = if (item.status == ScheduledStatus.PAUSED) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (item.status == ScheduledStatus.PAUSED) "Resume" else "Pause",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Edit, "Edit", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete", tint = Color(0xFFF44336), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
