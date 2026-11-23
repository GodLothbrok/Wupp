package com.wa.beastclient.core.calling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVoipVideoPolicyTest {
    @Test
    fun buildAvcEncoderCandidatesStartsWithVbrThenCbrAndDefaultFallbacks() {
        val candidates = buildAvcEncoderCandidates()
        assertEquals(4, candidates.size)
        assertEquals(
            VideoEncoderCandidate(480, 640, AvcBitrateMode.VBR, 800_000),
            candidates[0]
        )
        assertEquals(
            VideoEncoderCandidate(480, 640, AvcBitrateMode.CBR, 800_000),
            candidates[1]
        )
        assertEquals(
            VideoEncoderCandidate(480, 640, AvcBitrateMode.DEFAULT, 800_000),
            candidates[2]
        )
        assertEquals(
            VideoEncoderCandidate(368, 480, AvcBitrateMode.DEFAULT, 500_000),
            candidates[3]
        )
    }

    @Test
    fun selectBestCameraCaptureSizePrefersExactTargetWhenSupported() {
        val chosen = selectBestCameraCaptureSize(
            listOf(1280 to 720, 640 to 480, 320 to 240)
        )
        assertEquals(640 to 480, chosen)
    }

    @Test
    fun selectBestCameraCaptureSizeFallsBackCleanlyWhen640x480Unavailable() {
        val chosen = selectBestCameraCaptureSize(
            listOf(320 to 240, 1280 to 960, 720 to 540, 1920 to 1080)
        )
        assertEquals(720 to 540, chosen)
    }

    @Test
    fun selectBestCameraCaptureSizeReturnsDefaultWhenEmpty() {
        assertEquals(640 to 480, selectBestCameraCaptureSize(emptyList()))
    }

    @Test
    fun isSoftwareAvcCodecIdentifiesGoogleAndAospSoftwareCodecsOnly() {
        assertTrue(isSoftwareAvcCodec("OMX.google.h264.encoder"))
        assertTrue(isSoftwareAvcCodec("c2.android.avc.encoder"))
        assertTrue(isSoftwareAvcCodec("c2.mtk.sw.avc.encoder"))
        assertFalse(isSoftwareAvcCodec("c2.mtk.avc.encoder"))
        assertFalse(isSoftwareAvcCodec("OMX.qcom.video.encoder.avc"))
    }
}
