package com.wa.beastclient.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNotePlaybackPolicyTest {

    @Test
    fun nextSpeedIndex_cyclesThrough1x_1_5x_2x() {
        assertEquals(1, VoiceNotePlaybackManager.nextSpeedIndex(0))
        assertEquals(2, VoiceNotePlaybackManager.nextSpeedIndex(1))
        assertEquals(0, VoiceNotePlaybackManager.nextSpeedIndex(2))
        assertEquals(1.0f, VoiceNotePlaybackManager.speedForIndex(0), 0.001f)
        assertEquals(1.5f, VoiceNotePlaybackManager.speedForIndex(1), 0.001f)
        assertEquals(2.0f, VoiceNotePlaybackManager.speedForIndex(2), 0.001f)
        assertEquals(1.0f, VoiceNotePlaybackManager.speedForIndex(99), 0.001f)
    }

    @Test
    fun computePlaybackFraction_handlesBoundsAndZeroDuration() {
        assertEquals(0f, VoiceNotePlaybackManager.computePlaybackFraction(500L, 0L), 0.001f)
        assertEquals(0f, VoiceNotePlaybackManager.computePlaybackFraction(-100L, 10_000L), 0.001f)
        assertEquals(0.5f, VoiceNotePlaybackManager.computePlaybackFraction(5_000L, 10_000L), 0.001f)
        assertEquals(1f, VoiceNotePlaybackManager.computePlaybackFraction(15_000L, 10_000L), 0.001f)
    }

    @Test
    fun computeSeekPositionMs_clampsFractionToValidRange() {
        assertEquals(0L, VoiceNotePlaybackManager.computeSeekPositionMs(0.5f, 0L))
        assertEquals(0L, VoiceNotePlaybackManager.computeSeekPositionMs(-0.2f, 10_000L))
        assertEquals(2_500L, VoiceNotePlaybackManager.computeSeekPositionMs(0.25f, 10_000L))
        assertEquals(10_000L, VoiceNotePlaybackManager.computeSeekPositionMs(1.4f, 10_000L))
    }
}
