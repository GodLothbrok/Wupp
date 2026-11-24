package com.wa.beastclient.domain.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.domain.accounts.AccountManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages scheduled and recurring outbound messages with persistence,
 * exact/inexact OS alarm scheduling, and background dispatch.
 */
object ScheduledMessageManager {
    private const val TAG = "ScheduledMsgMgr"
    private const val PREFS_BASE = "wupp_scheduled_messages"
    private const val KEY_MESSAGES_JSON = "scheduled_messages_json"
    private const val ALARM_REQUEST_CODE = 88301

    private val _messages = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    val messages: StateFlow<List<ScheduledMessage>> = _messages.asStateFlow()

    @Volatile
    private var initialized = false

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            reloadLocked(context)
            initialized = true
        }
    }

    fun reloadForActiveAccount(context: Context) {
        synchronized(this) {
            reloadLocked(context)
            initialized = true
        }
    }

    private fun reloadLocked(context: Context) {
        AccountManager.init(context)
        prefs = AccountManager.getScopedPreferences(context, PREFS_BASE)
        val raw = prefs.getString(KEY_MESSAGES_JSON, null)
        _messages.value = decodeMessagesJson(raw)
    }

    fun scheduleMessage(context: Context, item: ScheduledMessage) {
        init(context)
        synchronized(this) {
            val current = _messages.value.toMutableList()
            current.removeAll { it.id == item.id }
            current.add(0, item)
            _messages.value = current
            persistLocked()
        }
        scheduleNextAlarm(context)
    }

    fun updateScheduledMessage(context: Context, item: ScheduledMessage) {
        init(context)
        synchronized(this) {
            val current = _messages.value.toMutableList()
            val index = current.indexOfFirst { it.id == item.id }
            if (index != -1) {
                current[index] = item
                _messages.value = current
                persistLocked()
            }
        }
        scheduleNextAlarm(context)
    }

    fun pauseScheduledMessage(context: Context, id: String) {
        init(context)
        synchronized(this) {
            val current = _messages.value.map {
                if (it.id == id) it.copy(status = ScheduledStatus.PAUSED) else it
            }
            _messages.value = current
            persistLocked()
        }
        scheduleNextAlarm(context)
    }

    fun resumeScheduledMessage(context: Context, id: String) {
        init(context)
        val now = System.currentTimeMillis()
        synchronized(this) {
            val current = _messages.value.map {
                if (it.id == id) {
                    val nextEpoch = if (it.scheduledEpochMs <= now) {
                        computeNextTriggerEpoch(it.scheduledEpochMs, it.repeatType, it.customDaysOfWeek, now) ?: (now + 60_000L)
                    } else {
                        it.scheduledEpochMs
                    }
                    it.copy(status = ScheduledStatus.PENDING, scheduledEpochMs = nextEpoch, failureReason = null)
                } else {
                    it
                }
            }
            _messages.value = current
            persistLocked()
        }
        scheduleNextAlarm(context)
    }

    fun deleteScheduledMessage(context: Context, id: String) {
        init(context)
        synchronized(this) {
            val current = _messages.value.filterNot { it.id == id }
            _messages.value = current
            persistLocked()
        }
        scheduleNextAlarm(context)
    }

    fun retryScheduledMessage(context: Context, id: String) {
        init(context)
        synchronized(this) {
            val now = System.currentTimeMillis()
            val current = _messages.value.map {
                if (it.id == id) {
                    it.copy(
                        status = ScheduledStatus.PENDING,
                        scheduledEpochMs = now + 2_000L,
                        failureReason = null
                    )
                } else {
                    it
                }
            }
            _messages.value = current
            persistLocked()
        }
        scheduleNextAlarm(context)
    }

    /**
     * Inspects all pending messages and dispatches any whose scheduled time has arrived.
     * Returns the count of messages processed.
     */
    suspend fun processDueMessages(context: Context): Int = withContext(Dispatchers.IO) {
        init(context)
        val now = System.currentTimeMillis()
        val dueList: List<ScheduledMessage>
        synchronized(ScheduledMessageManager) {
            dueList = _messages.value.filter {
                it.status == ScheduledStatus.PENDING && it.scheduledEpochMs <= now
            }
        }
        if (dueList.isEmpty()) return@withContext 0

        Log.i(TAG, "Processing ${dueList.size} due scheduled message(s)")
        var processedCount = 0

        for (item in dueList) {
            val result = runCatching {
                WaRealtimeService.sendTextMessage(
                    context = context,
                    chatJid = item.chatJid,
                    text = item.text,
                    quoted = null,
                    ghostMentionEveryone = false
                )
            }.getOrElse { Result.failure(it) }

            val updatedNow = System.currentTimeMillis()
            synchronized(ScheduledMessageManager) {
                val current = _messages.value.toMutableList()
                val idx = current.indexOfFirst { it.id == item.id }
                if (idx != -1) {
                    val updated = if (result.isSuccess) {
                        processedCount++
                        if (item.repeatType == ScheduledRepeatType.ONCE) {
                            item.copy(
                                status = ScheduledStatus.SENT,
                                lastTriggeredAtMs = updatedNow,
                                failureReason = null
                            )
                        } else {
                            val nextTime = computeNextTriggerEpoch(
                                item.scheduledEpochMs,
                                item.repeatType,
                                item.customDaysOfWeek,
                                updatedNow
                            )
                            if (nextTime != null) {
                                item.copy(
                                    scheduledEpochMs = nextTime,
                                    lastTriggeredAtMs = updatedNow,
                                    failureReason = null
                                )
                            } else {
                                item.copy(
                                    status = ScheduledStatus.SENT,
                                    lastTriggeredAtMs = updatedNow,
                                    failureReason = null
                                )
                            }
                        }
                    } else {
                        val errMsg = result.exceptionOrNull()?.message ?: "Failed to send message"
                        item.copy(
                            status = ScheduledStatus.FAILED,
                            lastTriggeredAtMs = updatedNow,
                            failureReason = errMsg
                        )
                    }
                    current[idx] = updated
                    _messages.value = current
                    persistLocked()
                }
            }
        }

        scheduleNextAlarm(context)
        processedCount
    }

    fun scheduleNextAlarm(context: Context) {
        val appCtx = context.applicationContext
        val now = System.currentTimeMillis()
        val nextPending = _messages.value
            .filter { it.status == ScheduledStatus.PENDING }
            .minByOrNull { it.scheduledEpochMs }

        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appCtx, ScheduledMessageReceiver::class.java).apply {
            action = ScheduledMessageReceiver.ACTION_FIRE_SCHEDULED
        }
        val pi = PendingIntent.getBroadcast(
            appCtx,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (nextPending == null) {
            alarmManager.cancel(pi)
            return
        }

        val triggerAt = nextPending.scheduledEpochMs.coerceAtLeast(now + 1_000L)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }.onFailure {
            runCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    private fun persistLocked() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putString(KEY_MESSAGES_JSON, encodeMessagesJson(_messages.value))
            .apply()
    }

    internal fun encodeMessagesJson(list: List<ScheduledMessage>): String {
        if (list.isEmpty()) return "[]"
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("chatJid", item.chatJid)
                put("chatTitle", item.chatTitle)
                put("text", item.text)
                put("scheduledEpochMs", item.scheduledEpochMs)
                put("repeatType", item.repeatType.name)
                put("status", item.status.name)
                put("createdAtMs", item.createdAtMs)
                put("lastTriggeredAtMs", item.lastTriggeredAtMs)
                item.failureReason?.let { put("failureReason", it) }

                val daysArr = JSONArray()
                item.customDaysOfWeek.forEach { daysArr.put(it) }
                put("customDaysOfWeek", daysArr)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    internal fun decodeMessagesJson(raw: String?): List<ScheduledMessage> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val result = ArrayList<ScheduledMessage>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val daysArr = obj.optJSONArray("customDaysOfWeek")
                val customDays = ArrayList<Int>()
                if (daysArr != null) {
                    for (d in 0 until daysArr.length()) {
                        customDays.add(daysArr.getInt(d))
                    }
                }
                result.add(
                    ScheduledMessage(
                        id = obj.optString("id"),
                        chatJid = obj.optString("chatJid"),
                        chatTitle = obj.optString("chatTitle"),
                        text = obj.optString("text"),
                        scheduledEpochMs = obj.optLong("scheduledEpochMs"),
                        repeatType = ScheduledRepeatType.fromStorageKey(obj.optString("repeatType")),
                        customDaysOfWeek = customDays,
                        status = ScheduledStatus.fromStorageKey(obj.optString("status")),
                        createdAtMs = obj.optLong("createdAtMs"),
                        lastTriggeredAtMs = obj.optLong("lastTriggeredAtMs"),
                        failureReason = if (obj.has("failureReason")) obj.getString("failureReason") else null
                    )
                )
            }
            result
        }.getOrDefault(emptyList())
    }
}
