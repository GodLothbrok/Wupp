package com.wa.beastclient.domain.scheduling

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ScheduledRepeatType(val label: String) {
    ONCE("One-time"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    CUSTOM_DAYS("Custom days");

    companion object {
        fun fromStorageKey(key: String?): ScheduledRepeatType = when (key?.uppercase(Locale.US)) {
            "DAILY" -> DAILY
            "WEEKLY" -> WEEKLY
            "CUSTOM_DAYS" -> CUSTOM_DAYS
            else -> ONCE
        }
    }
}

enum class ScheduledStatus(val label: String) {
    PENDING("Scheduled"),
    SENT("Sent"),
    FAILED("Failed"),
    PAUSED("Paused");

    companion object {
        fun fromStorageKey(key: String?): ScheduledStatus = when (key?.uppercase(Locale.US)) {
            "SENT" -> SENT
            "FAILED" -> FAILED
            "PAUSED" -> PAUSED
            else -> PENDING
        }
    }
}

data class ScheduledMessage(
    val id: String = UUID.randomUUID().toString(),
    val chatJid: String,
    val chatTitle: String = "",
    val text: String,
    val scheduledEpochMs: Long,
    val repeatType: ScheduledRepeatType = ScheduledRepeatType.ONCE,
    val customDaysOfWeek: List<Int> = emptyList(), // Calendar.SUNDAY(1) .. Calendar.SATURDAY(7)
    val status: ScheduledStatus = ScheduledStatus.PENDING,
    val createdAtMs: Long = System.currentTimeMillis(),
    val lastTriggeredAtMs: Long = 0L,
    val failureReason: String? = null
)

/**
 * Calculates the next trigger timestamp for a recurring scheduled message.
 * Returns null if the message is one-time or has no valid future recurrence.
 */
fun computeNextTriggerEpoch(
    baseEpochMs: Long,
    repeatType: ScheduledRepeatType,
    customDays: List<Int>,
    nowMs: Long = System.currentTimeMillis()
): Long? {
    if (repeatType == ScheduledRepeatType.ONCE) return null
    val cal = Calendar.getInstance().apply { timeInMillis = baseEpochMs }
    val targetHour = cal.get(Calendar.HOUR_OF_DAY)
    val targetMinute = cal.get(Calendar.MINUTE)
    val targetSecond = cal.get(Calendar.SECOND)

    val candidate = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, targetHour)
        set(Calendar.MINUTE, targetMinute)
        set(Calendar.SECOND, targetSecond)
        set(Calendar.MILLISECOND, 0)
    }

    return when (repeatType) {
        ScheduledRepeatType.DAILY -> {
            if (candidate.timeInMillis <= nowMs) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
            candidate.timeInMillis
        }
        ScheduledRepeatType.WEEKLY -> {
            val baseDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            candidate.set(Calendar.DAY_OF_WEEK, baseDayOfWeek)
            if (candidate.timeInMillis <= nowMs) {
                candidate.add(Calendar.WEEK_OF_YEAR, 1)
            }
            candidate.timeInMillis
        }
        ScheduledRepeatType.CUSTOM_DAYS -> {
            if (customDays.isEmpty()) return null
            val sortedDays = customDays.distinct()
            for (offset in 0..7) {
                val testCal = Calendar.getInstance().apply {
                    timeInMillis = nowMs
                    add(Calendar.DAY_OF_YEAR, offset)
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMinute)
                    set(Calendar.SECOND, targetSecond)
                    set(Calendar.MILLISECOND, 0)
                }
                if (testCal.timeInMillis > nowMs && testCal.get(Calendar.DAY_OF_WEEK) in sortedDays) {
                    return testCal.timeInMillis
                }
            }
            null
        }
        ScheduledRepeatType.ONCE -> null
    }
}

fun formatRecurrenceLabel(repeatType: ScheduledRepeatType, customDays: List<Int>): String = when (repeatType) {
    ScheduledRepeatType.ONCE -> "One-time"
    ScheduledRepeatType.DAILY -> "Every day"
    ScheduledRepeatType.WEEKLY -> "Every week"
    ScheduledRepeatType.CUSTOM_DAYS -> {
        if (customDays.isEmpty()) "Custom days"
        else {
            val dayNames = mapOf(
                Calendar.SUNDAY to "Sun",
                Calendar.MONDAY to "Mon",
                Calendar.TUESDAY to "Tue",
                Calendar.WEDNESDAY to "Wed",
                Calendar.THURSDAY to "Thu",
                Calendar.FRIDAY to "Fri",
                Calendar.SATURDAY to "Sat"
            )
            val sorted = customDays.distinct().sorted()
            sorted.mapNotNull { dayNames[it] }.joinToString(", ")
        }
    }
}

fun formatScheduledDateTime(epochMs: Long, is24Hour: Boolean = false): String {
    if (epochMs <= 0L) return "Not set"
    val pattern = if (is24Hour) "MMM d, yyyy · HH:mm" else "MMM d, yyyy · h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}
