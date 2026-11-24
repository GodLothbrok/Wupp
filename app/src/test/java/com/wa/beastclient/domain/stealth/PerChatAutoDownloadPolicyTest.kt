package com.wa.beastclient.domain.stealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerChatAutoDownloadPolicyTest {

    @Test
    fun formatAutoDownloadSummary_formatsMasksAccurately() {
        assertEquals("No media", StealthMatrixManager.formatAutoDownloadSummary(StealthMatrixManager.MEDIA_NONE))
        assertEquals("All media", StealthMatrixManager.formatAutoDownloadSummary(StealthMatrixManager.MEDIA_ALL))
        assertEquals(
            "Photos",
            StealthMatrixManager.formatAutoDownloadSummary(StealthMatrixManager.MEDIA_PHOTO)
        )
        assertEquals(
            "Photos, Audio",
            StealthMatrixManager.formatAutoDownloadSummary(
                StealthMatrixManager.MEDIA_PHOTO or StealthMatrixManager.MEDIA_AUDIO
            )
        )
        assertEquals(
            "Photos, Audio, Videos",
            StealthMatrixManager.formatAutoDownloadSummary(
                StealthMatrixManager.MEDIA_PHOTO or
                    StealthMatrixManager.MEDIA_VIDEO or
                    StealthMatrixManager.MEDIA_AUDIO
            )
        )
    }

    @Test
    fun globalStealthConfig_storesAndRetrievesPerChatOverrides() {
        val config = GlobalStealthConfig(
            perChatAutoDownload = mapOf(
                "chat1@s.whatsapp.net" to StealthMatrixManager.MEDIA_PHOTO,
                "chat2@g.us" to StealthMatrixManager.MEDIA_NONE
            )
        )

        assertEquals(
            StealthMatrixManager.MEDIA_PHOTO,
            config.perChatAutoDownload["chat1@s.whatsapp.net"]
        )
        assertEquals(
            StealthMatrixManager.MEDIA_NONE,
            config.perChatAutoDownload["chat2@g.us"]
        )
        assertEquals(null, config.perChatAutoDownload["chat3@s.whatsapp.net"])
    }
}
