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

    @Test
    fun activeAutoDownloadMask_respectsPerChatOverrideWhenGlobalIsDisabled() {
        val config = GlobalStealthConfig(
            autoDownloadEnabled = false,
            perChatAutoDownload = mapOf(
                "vip_chat@s.whatsapp.net" to (StealthMatrixManager.MEDIA_PHOTO or StealthMatrixManager.MEDIA_VIDEO)
            )
        )

        // For VIP chat with custom override: override must be respected even when global is OFF
        val maskForVip = StealthMatrixManager.activeAutoDownloadMask(
            context = null,
            cfg = config,
            chatJid = "vip_chat@s.whatsapp.net"
        )
        assertEquals(StealthMatrixManager.MEDIA_PHOTO or StealthMatrixManager.MEDIA_VIDEO, maskForVip)

        // For ordinary chat without override: must return MEDIA_NONE because global is OFF
        val maskForOther = StealthMatrixManager.activeAutoDownloadMask(
            context = null,
            cfg = config,
            chatJid = "other_chat@s.whatsapp.net"
        )
        assertEquals(StealthMatrixManager.MEDIA_NONE, maskForOther)
    }

    @Test
    fun activeAutoDownloadMask_respectsNoMediaOverrideWhenGlobalIsEnabled() {
        val config = GlobalStealthConfig(
            autoDownloadEnabled = true,
            autoDownloadWifiMask = StealthMatrixManager.MEDIA_ALL,
            perChatAutoDownload = mapOf(
                "muted_chat@s.whatsapp.net" to StealthMatrixManager.MEDIA_NONE
            )
        )

        // For muted chat with MEDIA_NONE: must return MEDIA_NONE even when global is ON
        val mask = StealthMatrixManager.activeAutoDownloadMask(
            context = null,
            cfg = config,
            chatJid = "muted_chat@s.whatsapp.net"
        )
        assertEquals(StealthMatrixManager.MEDIA_NONE, mask)
    }

    @Test
    fun isAutoDownloadAllowed_correctlyAppliesPerChatRules() {
        val config = GlobalStealthConfig(
            autoDownloadEnabled = false,
            perChatAutoDownload = mapOf(
                "vip_chat@s.whatsapp.net" to StealthMatrixManager.MEDIA_PHOTO,
                "block_chat@s.whatsapp.net" to StealthMatrixManager.MEDIA_NONE
            )
        )

        // VIP chat: Photos allowed even with global disabled
        assertTrue(
            StealthMatrixManager.isAutoDownloadAllowed(
                context = null,
                msgType = "IMAGE",
                cfg = config,
                chatJid = "vip_chat@s.whatsapp.net"
            )
        )

        // VIP chat: Videos NOT allowed
        assertFalse(
            StealthMatrixManager.isAutoDownloadAllowed(
                context = null,
                msgType = "VIDEO",
                cfg = config,
                chatJid = "vip_chat@s.whatsapp.net"
            )
        )

        // Blocked chat: Photos NOT allowed
        assertFalse(
            StealthMatrixManager.isAutoDownloadAllowed(
                context = null,
                msgType = "IMAGE",
                cfg = config,
                chatJid = "block_chat@s.whatsapp.net"
            )
        )
    }

    @Test
    fun getPerChatAutoDownloadJson_serializesMapToJson() {
        val config = GlobalStealthConfig(
            perChatAutoDownload = mapOf(
                "chatA@s.whatsapp.net" to 3,
                "chatB@g.us" to 0
            )
        )
        val json = StealthMatrixManager.getPerChatAutoDownloadJson(config)
        assertTrue(json.contains("\"chatA@s.whatsapp.net\":3"))
        assertTrue(json.contains("\"chatB@g.us\":0"))
    }
}
