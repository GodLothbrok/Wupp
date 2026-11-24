package com.wa.beastclient.domain.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduledMessagePolicyTest {

    @Test
    fun computeNextTriggerEpoch_onceReturnsNull() {
        val now = 1_700_000_000_000L
        val next = computeNextTriggerEpoch(
            baseEpochMs = now - 1000L,
            repeatType = ScheduledRepeatType.ONCE,
            customDays = emptyList(),
            nowMs = now
        )
        assertNull(next)
    }

    @Test
    fun computeNextTriggerEpoch_dailyCalculatesFutureEpoch() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 10, 0, 0)
        }
        val now = nowCal.timeInMillis

        // Base time was 9:00 AM (already passed today) -> should schedule for tomorrow at 9:00 AM
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 9, 0, 0)
        }
        val next = computeNextTriggerEpoch(
            baseEpochMs = baseCal.timeInMillis,
            repeatType = ScheduledRepeatType.DAILY,
            customDays = emptyList(),
            nowMs = now
        )
        assertNotNull(next)
        assertTrue(next!! > now)

        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(25, nextCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextCal.get(Calendar.MINUTE))
    }

    @Test
    fun computeNextTriggerEpoch_weeklyCalculatesNextWeek() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 12, 0, 0)
        }
        val now = nowCal.timeInMillis

        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 8, 0, 0) // earlier today
        }
        val next = computeNextTriggerEpoch(
            baseEpochMs = baseCal.timeInMillis,
            repeatType = ScheduledRepeatType.WEEKLY,
            customDays = emptyList(),
            nowMs = now
        )
        assertNotNull(next)
        assertTrue(next!! > now)

        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(baseCal.get(Calendar.DAY_OF_WEEK), nextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, nextCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun computeNextTriggerEpoch_customDaysPicksNextMatchingDay() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 12, 0, 0) // Tuesday
        }
        val now = nowCal.timeInMillis

        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 24, 9, 0, 0)
        }
        // Custom days: Wednesday and Friday
        val next = computeNextTriggerEpoch(
            baseEpochMs = baseCal.timeInMillis,
            repeatType = ScheduledRepeatType.CUSTOM_DAYS,
            customDays = listOf(Calendar.WEDNESDAY, Calendar.FRIDAY),
            nowMs = now
        )
        assertNotNull(next)
        assertTrue(next!! > now)

        val nextCal = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(Calendar.WEDNESDAY, nextCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(9, nextCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun formatRecurrenceLabel_formatsNicely() {
        assertEquals("One-time", formatRecurrenceLabel(ScheduledRepeatType.ONCE, emptyList()))
        assertEquals("Every day", formatRecurrenceLabel(ScheduledRepeatType.DAILY, emptyList()))
        assertEquals("Every week", formatRecurrenceLabel(ScheduledRepeatType.WEEKLY, emptyList()))
        assertEquals(
            "Mon, Wed, Fri",
            formatRecurrenceLabel(
                ScheduledRepeatType.CUSTOM_DAYS,
                listOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
            )
        )
    }

    @Test
    fun scheduledMessage_statusTransitionsAndCopies() {
        val original = ScheduledMessage(
            id = "sched-1",
            chatJid = "123456789@s.whatsapp.net",
            chatTitle = "Alice",
            text = "Happy birthday!",
            scheduledEpochMs = 1_700_100_000_000L,
            repeatType = ScheduledRepeatType.ONCE,
            status = ScheduledStatus.PENDING
        )

        val paused = original.copy(status = ScheduledStatus.PAUSED)
        assertEquals(ScheduledStatus.PAUSED, paused.status)

        val sent = original.copy(status = ScheduledStatus.SENT, lastTriggeredAtMs = 1_700_100_001_000L)
        assertEquals(ScheduledStatus.SENT, sent.status)
        assertEquals(1_700_100_001_000L, sent.lastTriggeredAtMs)

        val failed = original.copy(status = ScheduledStatus.FAILED, failureReason = "Companion offline")
        assertEquals(ScheduledStatus.FAILED, failed.status)
        assertEquals("Companion offline", failed.failureReason)
    }

    @Test
    fun scheduledRepeatType_fromStorageKeyResolvesCorrectly() {
        assertEquals(ScheduledRepeatType.DAILY, ScheduledRepeatType.fromStorageKey("DAILY"))
        assertEquals(ScheduledRepeatType.WEEKLY, ScheduledRepeatType.fromStorageKey("weekly"))
        assertEquals(ScheduledRepeatType.CUSTOM_DAYS, ScheduledRepeatType.fromStorageKey("CUSTOM_DAYS"))
        assertEquals(ScheduledRepeatType.ONCE, ScheduledRepeatType.fromStorageKey("unknown"))
        assertEquals(ScheduledRepeatType.ONCE, ScheduledRepeatType.fromStorageKey(null))
    }

    @Test
    fun scheduledStatus_fromStorageKeyResolvesCorrectly() {
        assertEquals(ScheduledStatus.PENDING, ScheduledStatus.fromStorageKey("PENDING"))
        assertEquals(ScheduledStatus.SENT, ScheduledStatus.fromStorageKey("sent"))
        assertEquals(ScheduledStatus.FAILED, ScheduledStatus.fromStorageKey("FAILED"))
        assertEquals(ScheduledStatus.PAUSED, ScheduledStatus.fromStorageKey("PAUSED"))
        assertEquals(ScheduledStatus.PENDING, ScheduledStatus.fromStorageKey(null))
    }
}
