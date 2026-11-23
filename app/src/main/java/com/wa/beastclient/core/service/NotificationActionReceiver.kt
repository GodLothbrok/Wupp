package com.wa.beastclient.core.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.wa.beastclient.data.db.WaDao
import com.wa.beastclient.data.db.WaUiDatabase
import com.wa.beastclient.domain.security.AppLockManager
import kotlinx.coroutines.CancellationException
import com.wa.beastclient.domain.stealth.StealthMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles inline quick-reply and "Mark as read" actions directly from Android message notifications.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != ACTION_INLINE_REPLY && action != ACTION_MARK_READ) return
        val chatJid = intent.getStringExtra(EXTRA_CHAT_JID)?.trim().orEmpty()
        if (chatJid.isBlank()) return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val appCtx = context.applicationContext

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var replySent = false
            try {
                StealthMatrixManager.init(appCtx)
                AppLockManager.init(appCtx)
                val dao = WaUiDatabase.getInstance(appCtx).waDao()
                val policy = resolveNotificationActionPolicy(
                    notifyShowPreview = StealthMatrixManager.config.value.notifyShowPreview,
                    isChannel = chatJid.endsWith("@newsletter"),
                    isAnnounceLocked = chatJid in WaRealtimeService.announceLockedGroups.value,
                    isPrivateConversation = isPrivateMessageNotification(dao, chatJid)
                )
                val allowed = if (action == ACTION_INLINE_REPLY) policy.showReplyAction else policy.showMarkReadAction
                if (!allowed) {
                    WaRealtimeService.refreshMessageNotificationAfterAction(appCtx, chatJid)
                    return@launch
                }
                when (action) {
                    ACTION_INLINE_REPLY -> {
                        val replyText = RemoteInput.getResultsFromIntent(intent)
                            ?.getCharSequence(KEY_REPLY_TEXT)
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                        if (replyText.isBlank()) {
                            WaRealtimeService.refreshMessageNotificationAfterAction(appCtx, chatJid)
                            return@launch
                        }
                        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty()
                        val isGroup = intent.getBooleanExtra(EXTRA_IS_GROUP, chatJid.endsWith("@g.us"))
                        val quotedMsg = if (isGroup && messageId.isNotBlank()) {
                            dao.getMessage(messageId)?.takeIf { it.chatJid == chatJid }
                        } else {
                            null
                        }

                        val sentEntity = WaRealtimeService.sendTextMessage(
                            context = appCtx,
                            chatJid = chatJid,
                            text = replyText,
                            quoted = quotedMsg,
                            ghostMentionEveryone = false,
                            requireLinkedSession = true
                        ).getOrThrow()
                        replySent = true
                        markNotificationChatRead(appCtx, dao, sentEntity.chatJid)
                        WaRealtimeService.appendSelfReplyToNotification(
                            context = appCtx,
                            chatJid = chatJid,
                            replyText = replyText,
                            timestampMs = sentEntity.timestamp
                        )
                    }

                    ACTION_MARK_READ -> {
                        markNotificationChatRead(appCtx, dao, chatJid)
                        WaRealtimeService.clearNotificationHistory(chatJid)
                        if (notifId != 0) {
                            val mgr = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                            mgr?.cancel(notifId)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (err: Exception) {
                runCatching { WaRealtimeService.refreshMessageNotificationAfterAction(appCtx, chatJid) }
                val message = when {
                    replySent -> "Reply sent, but the notification couldn't be updated"
                    action == ACTION_INLINE_REPLY -> err.message?.takeIf { it.isNotBlank() } ?: "Unable to send reply"
                    else -> "Unable to mark as read"
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appCtx, message, Toast.LENGTH_LONG).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun markNotificationChatRead(context: Context, dao: WaDao, chatJid: String) {
        val privacy = StealthMatrixManager.getEffectivePrivacy(dao, chatJid)
        val offline = StealthMatrixManager.config.value.offlineMode
        if (shouldSendReadReceiptOnNotificationAction(privacy.sendReadReceipts, offline)) {
            WaRealtimeService.markChatReadExplicitly(context, chatJid).getOrThrow()
        } else {
            dao.markIncomingLocallyRead(chatJid)
            dao.clearLocalUnread(chatJid)
        }
    }

    companion object {
        const val ACTION_INLINE_REPLY = "com.wupp.app.action.NOTIFICATION_INLINE_REPLY"
        const val ACTION_MARK_READ = "com.wupp.app.action.NOTIFICATION_MARK_READ"
        const val KEY_REPLY_TEXT = "key_notification_reply_text"
        const val EXTRA_CHAT_JID = "extra_chat_jid"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_IS_GROUP = "extra_is_group"
    }
}

internal data class NotificationActionPolicy(
    val showReplyAction: Boolean,
    val showMarkReadAction: Boolean
)

internal fun resolveNotificationActionPolicy(
    notifyShowPreview: Boolean,
    isChannel: Boolean,
    isAnnounceLocked: Boolean,
    isPrivateConversation: Boolean = false
): NotificationActionPolicy {
    if (isChannel || !notifyShowPreview || isPrivateConversation) {
        return NotificationActionPolicy(showReplyAction = false, showMarkReadAction = false)
    }
    return NotificationActionPolicy(
        showReplyAction = !isAnnounceLocked,
        showMarkReadAction = true
    )
}

internal fun shouldSendReadReceiptOnNotificationAction(
    sendReadReceipts: Boolean,
    offlineMode: Boolean
): Boolean = sendReadReceipts && !offlineMode

internal suspend fun isPrivateMessageNotification(dao: WaDao, chatJid: String): Boolean =
    AppLockManager.isLocked.value ||
        AppLockManager.shouldSuppressAlertsForChat(dao.getChat(chatJid)?.isLocked == true)
