package com.wa.beastclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.wa.beastclient.domain.accounts.AccountManager
import com.wa.beastclient.domain.accounts.WuppAccount
import com.wa.beastclient.ui.theme.LocalWaColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    accounts: List<WuppAccount>,
    activeAccountId: String,
    isSwitching: Boolean,
    onSelectAccount: (String) -> Unit,
    onAddAccount: (String) -> Unit,
    onRenameAccount: (String, String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWaColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<WuppAccount?>(null) }
    var deleteTarget by remember { mutableStateOf<WuppAccount?>(null) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSwitching) onDismiss() },
        sheetState = sheetState,
        containerColor = colors.elevatedBar,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.pillActiveBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ManageAccounts,
                        contentDescription = null,
                        tint = colors.brightGreen
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WhatsApp Accounts",
                        color = colors.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "One active session runs at a time. Chats, media, and settings stay separated.",
                        color = colors.textSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            if (isSwitching) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.pillActiveBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.brightGreen
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Switching WhatsApp session…",
                        color = colors.pillActiveText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    val isActive = account.id == activeAccountId
                    val phone = account.displayPhone
                    val subtitle = buildString {
                        if (phone.isNotBlank()) {
                            append(phone)
                            if (account.pushName.isNotBlank() && account.pushName != account.displayTitle) {
                                append(" · ")
                                append(account.pushName)
                            }
                        } else if (account.ownJid.isNotBlank()) {
                            append(account.ownJid.substringBefore("@"))
                        } else {
                            append("Not linked yet")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isActive) colors.pillActiveBg.copy(alpha = 0.55f)
                                else colors.appSurface.copy(alpha = 0.72f)
                            )
                            .border(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) colors.brightGreen else colors.divider,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = !isSwitching) {
                                if (!isActive) {
                                    onSelectAccount(account.id)
                                    onDismiss()
                                } else {
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) colors.accentGreen
                                    else colors.pillInactiveBg
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = account.displayTitle.take(1).uppercase(),
                                color = if (isActive) Color.White else colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = account.displayTitle,
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isActive) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.pillActiveBg)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = colors.brightGreen,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Active",
                                            color = colors.pillActiveText,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = subtitle,
                                color = colors.textSecondary,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { renameTarget = account },
                            enabled = !isSwitching
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Rename account",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (!isActive && account.id != AccountManager.DEFAULT_ACCOUNT_ID) {
                            IconButton(
                                onClick = { deleteTarget = account },
                                enabled = !isSwitching
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remove account",
                                    tint = colors.antiDeleteRed,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showAddDialog = true },
                enabled = !isSwitching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentGreen,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add another WhatsApp account",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showAddDialog) {
        var labelInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add WhatsApp account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Creates an isolated session and storage folder, then opens the link screen so you can scan QR or pair by phone number.",
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        singleLine = true,
                        label = { Text("Label (optional)") },
                        placeholder = { Text("Work, Personal, Alt…") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        onAddAccount(labelInput)
                        onDismiss()
                    }
                ) {
                    Text("Create & Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    renameTarget?.let { target ->
        var labelInput by remember(target.id) { mutableStateOf(target.label) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename account") },
            text = {
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    singleLine = true,
                    label = { Text("Account label") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = labelInput.trim().isNotBlank(),
                    onClick = {
                        onRenameAccount(target.id, labelInput)
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove ${target.displayTitle}?") },
            text = {
                Text("This deletes this account's local Wupp database, linked session keys, and vault media on this phone. It does not affect your main WhatsApp phone app.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveAccount(target.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Remove", color = colors.antiDeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
