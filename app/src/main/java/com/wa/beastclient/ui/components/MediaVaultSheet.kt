package com.wa.beastclient.ui.components

import android.app.Activity
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.db.StatusEntity
import com.wa.beastclient.data.media.EagerMediaVault
import com.wa.beastclient.domain.security.AppLockConfig
import com.wa.beastclient.domain.security.AppLockManager
import com.wa.beastclient.domain.security.AppLockMode
import com.wa.beastclient.ui.theme.LocalWaColors
import com.wa.beastclient.ui.theme.LocalWaMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface VaultFolderTarget {
    data object Root : VaultFolderTarget
    data object ViewOnce : VaultFolderTarget
    data object Statuses : VaultFolderTarget
    data object CallRecordings : VaultFolderTarget
    data object Avatars : VaultFolderTarget
    data class Custom(val folderName: String) : VaultFolderTarget
}

private data class VaultFileItem(
    val title: String,
    val subtitle: String,
    val file: File,
    val mimeType: String,
    val timestampMs: Long
)

private fun guessVaultMimeType(file: File): String = when (file.extension.lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "mp4", "mkv" -> "video/mp4"
    "wav" -> "audio/wav"
    "ogg", "opus" -> "audio/ogg"
    "m4a" -> "audio/mp4"
    else -> "image/jpeg"
}

private fun formatFileBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

private fun formatVaultDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    return SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(epochMs))
}

/**
 * Locked Media Vault sheet organizing View Once captures, Saved Statuses, Call Recordings,
 * Saved Avatars, and user custom folders behind device biometrics or Wupp PIN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVaultSheet(
    viewOnceMessages: List<MessageEntity>,
    statuses: List<StatusEntity>,
    vaultLockEnabled: Boolean,
    appLockConfig: AppLockConfig,
    onToggleVaultLock: (Boolean) -> Unit,
    onExportFileToGallery: (File, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalWaColors.current
    val mono = LocalWaMonoFamily.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var unlocked by remember(vaultLockEnabled) { mutableStateOf(!vaultLockEnabled) }
    var lockError by remember { mutableStateOf<String?>(null) }
    var pinEntry by remember { mutableStateOf("") }

    var activeFolder by remember { mutableStateOf<VaultFolderTarget>(VaultFolderTarget.Root) }
    var folderRefreshTick by remember { mutableIntStateOf(0) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var previewMediaItem by remember { mutableStateOf<VaultFileItem?>(null) }

    // Inline audio playback state for Call Recordings folder
    var activeAudioPath by remember { mutableStateOf<String?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    val keyguardLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            unlocked = true
            lockError = null
        }
    }

    val importCustomLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val customTarget = activeFolder as? VaultFolderTarget.Custom ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val dir = EagerMediaVault.createCustomVaultFolder(context, customTarget.folderName)
                val imported = EagerMediaVault.importUriToVaultFolder(context, uri, dir)
                if (imported != null) {
                    folderRefreshTick++
                    Toast.makeText(context, "Saved to ${customTarget.folderName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun triggerUnlockPrompt() {
        val activity = context.findFragmentActivity()
        if (appLockConfig.mode == AppLockMode.CUSTOM_PIN && appLockConfig.hasCustomPin) {
            // Inline PIN field already handles custom PIN directly.
            return
        }
        if (activity != null) {
            launchDeviceAuthPrompt(
                activity = activity,
                title = "Unlock Media Vault",
                subtitle = "Verify identity to view private vault folders",
                cancelText = "Authentication cancelled",
                keyguardFallbackLauncher = { intent -> keyguardLauncher.launch(intent) },
                onAuthenticated = {
                    unlocked = true
                    lockError = null
                },
                onErrorMessage = { msg -> lockError = msg }
            )
        } else {
            lockError = "No biometric/device credential lock available on this screen"
        }
    }

    LaunchedEffect(vaultLockEnabled, unlocked) {
        if (vaultLockEnabled && !unlocked && appLockConfig.mode != AppLockMode.CUSTOM_PIN) {
            triggerUnlockPrompt()
        }
    }

    val customFolders = remember(folderRefreshTick) { EagerMediaVault.listCustomVaultFolders(context) }
    val callRecordFiles = remember(folderRefreshTick) {
        EagerMediaVault.callRecordingsVaultDir(context)
            .listFiles()
            ?.filter { it.isFile && it.length() > 44L }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }
    val avatarFiles = remember(folderRefreshTick) {
        EagerMediaVault.avatarVaultDir(context)
            .listFiles()
            ?.filter { it.isFile && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }
    val statusMediaItems = remember(statuses, folderRefreshTick) {
        val fromDb = statuses.mapNotNull { st ->
            val f = st.localMediaPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L } ?: return@mapNotNull null
            VaultFileItem(
                title = st.senderName.ifBlank { st.senderJid.substringBefore("@") },
                subtitle = (if (st.isRevoked) "Revoked · " else "") + formatVaultDate(st.timestamp),
                file = f,
                mimeType = guessVaultMimeType(f),
                timestampMs = st.timestamp
            )
        }
        val knownPaths = fromDb.mapTo(HashSet()) { it.file.absolutePath }
        val extraFiles = EagerMediaVault.statusVaultDir(context)
            .listFiles()
            ?.filter { it.isFile && it.length() > 0L && !knownPaths.contains(it.absolutePath) }
            ?.map { f ->
                VaultFileItem(
                    title = f.nameWithoutExtension,
                    subtitle = formatVaultDate(f.lastModified()),
                    file = f,
                    mimeType = guessVaultMimeType(f),
                    timestampMs = f.lastModified()
                )
            }
            .orEmpty()
        (fromDb + extraFiles).sortedByDescending { it.timestampMs }
    }
    val viewOnceItems = remember(viewOnceMessages, folderRefreshTick) {
        val fromDb = viewOnceMessages.mapNotNull { msg ->
            val f = msg.localMediaPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L } ?: return@mapNotNull null
            VaultFileItem(
                title = msg.senderName.ifBlank { msg.chatJid.substringBefore("@") },
                subtitle = formatVaultDate(msg.timestamp),
                file = f,
                mimeType = msg.mimeType.ifBlank { guessVaultMimeType(f) },
                timestampMs = msg.timestamp
            )
        }
        val knownPaths = fromDb.mapTo(HashSet()) { it.file.absolutePath }
        val extraFiles = EagerMediaVault.viewOnceVaultDir(context)
            .listFiles()
            ?.filter { it.isFile && it.length() > 0L && !knownPaths.contains(it.absolutePath) }
            ?.map { f ->
                VaultFileItem(
                    title = f.nameWithoutExtension,
                    subtitle = formatVaultDate(f.lastModified()),
                    file = f,
                    mimeType = guessVaultMimeType(f),
                    timestampMs = f.lastModified()
                )
            }
            .orEmpty()
        (fromDb + extraFiles).sortedByDescending { it.timestampMs }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.elevatedBar
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeFolder != VaultFolderTarget.Root && unlocked) {
                        IconButton(onClick = { activeFolder = VaultFolderTarget.Root }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Folders", tint = colors.textPrimary)
                        }
                    } else {
                        Icon(Icons.Rounded.Shield, null, tint = colors.accentGreen, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        val headerTitle = when (val target = activeFolder) {
                            VaultFolderTarget.Root -> "Media Vault"
                            VaultFolderTarget.ViewOnce -> "View Once Saves"
                            VaultFolderTarget.Statuses -> "Saved Statuses"
                            VaultFolderTarget.CallRecordings -> "Call Recordings"
                            VaultFolderTarget.Avatars -> "Saved Avatars"
                            is VaultFolderTarget.Custom -> target.folderName
                        }
                        Text(headerTitle, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (!unlocked) "Locked private storage" else "Stored offline on your device",
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = colors.textSecondary)
                }
            }

            if (!unlocked) {
                // Vault Lock Gate
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.appSurface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(colors.pillActiveBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = colors.accentGreen, modifier = Modifier.size(28.dp))
                        }
                        Text("Media Vault Locked", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Biometric or PIN verification required to view private media folders.",
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                        if (appLockConfig.mode == AppLockMode.CUSTOM_PIN && appLockConfig.hasCustomPin) {
                            OutlinedTextField(
                                value = pinEntry,
                                onValueChange = { next ->
                                    val digits = next.filter { it.isDigit() }.take(8)
                                    pinEntry = digits
                                    if (digits.length >= 4 && AppLockManager.verifyCustomPin(digits)) {
                                        unlocked = true
                                        lockError = null
                                    }
                                },
                                label = { Text("Enter Wupp PIN") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(0.75f)
                            )
                        }
                        lockError?.let { err ->
                            Text(err, color = colors.antiDeleteRed, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                if (appLockConfig.mode == AppLockMode.CUSTOM_PIN && appLockConfig.hasCustomPin) {
                                    if (AppLockManager.verifyCustomPin(pinEntry)) {
                                        unlocked = true
                                        lockError = null
                                    } else {
                                        lockError = "Incorrect PIN"
                                    }
                                } else {
                                    triggerUnlockPrompt()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                        ) {
                            Icon(Icons.Rounded.LockOpen, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Unlock Vault", color = Color.White)
                        }
                    }
                }
            } else {
                // Vault Lock Toggle Bar inside Root
                if (activeFolder == VaultFolderTarget.Root) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.appSurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Require Lock to Open Vault", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Protects saved View Once, Statuses & Recordings", color = colors.textSecondary, fontSize = 11.5.sp)
                        }
                        Switch(
                            checked = vaultLockEnabled,
                            onCheckedChange = onToggleVaultLock,
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.accentGreen, checkedThumbColor = Color.White)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Folders", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Rounded.CreateNewFolder, null, tint = colors.accentGreen, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("New Folder", color = colors.accentGreen, fontSize = 13.sp)
                        }
                    }

                    val folderCards = buildList {
                        add(Triple("View Once", "${viewOnceItems.size} items", Icons.Rounded.VisibilityOff to VaultFolderTarget.ViewOnce))
                        add(Triple("Statuses", "${statusMediaItems.size} items", Icons.Rounded.AutoStories to VaultFolderTarget.Statuses))
                        add(Triple("Call Recordings", "${callRecordFiles.size} calls", Icons.Rounded.Call to VaultFolderTarget.CallRecordings))
                        add(Triple("Saved Avatars", "${avatarFiles.size} photos", Icons.Rounded.Face to VaultFolderTarget.Avatars))
                        customFolders.forEach { dir ->
                            val count = dir.listFiles()?.count { it.isFile } ?: 0
                            add(Triple(dir.name, "$count files", Icons.Rounded.Folder to VaultFolderTarget.Custom(dir.name)))
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(folderCards) { (title, countLabel, iconAndTarget) ->
                            val (icon, target) = iconAndTarget
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colors.appSurface),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeFolder = target }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.pillActiveBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, null, tint = colors.accentGreen, modifier = Modifier.size(20.dp))
                                    }
                                    Text(
                                        text = title,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(countLabel, color = colors.textSecondary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else if (activeFolder == VaultFolderTarget.CallRecordings) {
                    // Call Recordings List with Inline WAV Player
                    if (callRecordFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("No recorded calls yet — tap Record during any call.", color = colors.textSecondary, fontSize = 13.5.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(callRecordFiles, key = { it.absolutePath }) { file ->
                                val isThisPlaying = activeAudioPath == file.absolutePath && isAudioPlaying
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colors.appSurface),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (isThisPlaying) {
                                                    runCatching { mediaPlayer.pause() }
                                                    isAudioPlaying = false
                                                } else {
                                                    runCatching {
                                                        mediaPlayer.reset()
                                                        mediaPlayer.setDataSource(file.absolutePath)
                                                        mediaPlayer.setOnCompletionListener { isAudioPlaying = false }
                                                        mediaPlayer.prepare()
                                                        mediaPlayer.start()
                                                        activeAudioPath = file.absolutePath
                                                        isAudioPlaying = true
                                                    }.onFailure {
                                                        Toast.makeText(context, "Unable to play recording", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(colors.pillActiveBg)
                                        ) {
                                            Icon(
                                                imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                contentDescription = "Play call recording",
                                                tint = colors.accentGreen
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.nameWithoutExtension.removePrefix("call_"),
                                                color = colors.textPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${formatVaultDate(file.lastModified())} · ${formatFileBytes(file.length())}",
                                                color = colors.textSecondary,
                                                fontSize = 11.5.sp,
                                                fontFamily = mono
                                            )
                                        }
                                        IconButton(onClick = { onExportFileToGallery(file, file.name, "audio/wav") }) {
                                            Icon(Icons.Rounded.Download, "Save to Downloads", tint = colors.accentGreen)
                                        }
                                        IconButton(onClick = {
                                            if (activeAudioPath == file.absolutePath) {
                                                runCatching { mediaPlayer.stop() }
                                                isAudioPlaying = false
                                                activeAudioPath = null
                                            }
                                            file.delete()
                                            folderRefreshTick++
                                        }) {
                                            Icon(Icons.Rounded.DeleteOutline, "Delete recording", tint = colors.antiDeleteRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Media Grid for ViewOnce, Statuses, Avatars, or Custom Folder
                    val customFolder = activeFolder as? VaultFolderTarget.Custom
                    if (customFolder != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    importCustomLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Import Media", fontSize = 12.5.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    EagerMediaVault.deleteCustomVaultFolder(context, customFolder.folderName)
                                    activeFolder = VaultFolderTarget.Root
                                    folderRefreshTick++
                                }
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = colors.antiDeleteRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete Folder", color = colors.antiDeleteRed, fontSize = 12.5.sp)
                            }
                        }
                    }

                    val gridItems: List<VaultFileItem> = when (activeFolder) {
                        VaultFolderTarget.ViewOnce -> viewOnceItems
                        VaultFolderTarget.Statuses -> statusMediaItems
                        VaultFolderTarget.Avatars -> avatarFiles.map { f ->
                            VaultFileItem(
                                title = f.nameWithoutExtension,
                                subtitle = formatVaultDate(f.lastModified()),
                                file = f,
                                mimeType = guessVaultMimeType(f),
                                timestampMs = f.lastModified()
                            )
                        }
                        is VaultFolderTarget.Custom -> EagerMediaVault.createCustomVaultFolder(context, customFolder!!.folderName)
                            .listFiles()
                            ?.filter { it.isFile && it.length() > 0L }
                            ?.sortedByDescending { it.lastModified() }
                            ?.map { f ->
                                VaultFileItem(
                                    title = f.nameWithoutExtension,
                                    subtitle = formatVaultDate(f.lastModified()),
                                    file = f,
                                    mimeType = guessVaultMimeType(f),
                                    timestampMs = f.lastModified()
                                )
                            }
                            .orEmpty()
                        else -> emptyList()
                    }

                    if (gridItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("This folder is empty.", color = colors.textSecondary, fontSize = 13.5.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(gridItems, key = { it.file.absolutePath }) { item ->
                                val thumb by produceState<ImageBitmap?>(initialValue = null, item.file.absolutePath) {
                                    value = withContext(Dispatchers.IO) {
                                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                                        BitmapFactory.decodeFile(item.file.absolutePath, opts)?.asImageBitmap()
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.appSurface)
                                        .border(0.7.dp, colors.divider, RoundedCornerShape(12.dp))
                                        .clickable { previewMediaItem = item },
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb!!,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0x99000000))
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            containerColor = colors.elevatedBar,
            title = { Text("Create Vault Folder", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it.take(36) },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            EagerMediaVault.createCustomVaultFolder(context, newFolderName)
                            newFolderName = ""
                            folderRefreshTick++
                            showCreateFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    previewMediaItem?.let { item ->
        val fullBmp by produceState<ImageBitmap?>(initialValue = null, item.file.absolutePath) {
            value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(item.file.absolutePath)?.asImageBitmap()
            }
        }
        AlertDialog(
            onDismissRequest = { previewMediaItem = null },
            containerColor = colors.elevatedBar,
            title = { Text(item.title, color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    fullBmp?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = item.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text("${item.subtitle} · ${formatFileBytes(item.file.length())}", color = colors.textSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onExportFileToGallery(item.file, item.file.name, item.mimeType)
                        previewMediaItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentGreen)
                ) {
                    Text("Save to Gallery", color = Color.White)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            item.file.delete()
                            previewMediaItem = null
                            folderRefreshTick++
                        }
                    ) {
                        Text("Delete", color = colors.antiDeleteRed)
                    }
                    TextButton(onClick = { previewMediaItem = null }) { Text("Close") }
                }
            }
        )
    }
}
