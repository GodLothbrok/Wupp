package com.wa.beastclient.ui

import androidx.compose.ui.graphics.Color
import com.wa.beastclient.core.calling.CallRecordingWriter
import com.wa.beastclient.core.calling.mixCallPcmSample
import com.wa.beastclient.core.service.WaRealtimeService
import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.MessageEditEntity
import com.wa.beastclient.data.db.MessageEntity
import com.wa.beastclient.data.media.EagerMediaVault
import com.wa.beastclient.ui.components.MessageHistoryFilter
import com.wa.beastclient.ui.components.buildMessageHistoryEntries
import com.wa.beastclient.ui.components.shouldEraseBackgroundPixel
import com.wa.beastclient.ui.theme.WuppAccentColor
import com.wa.beastclient.ui.theme.WuppFontStyle
import com.wa.beastclient.ui.theme.WuppThemeMode
import com.wa.beastclient.ui.theme.colorsForTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.path.createTempDirectory

class WatusiFeaturesUnitTest {

    @Test
    fun pureBlackOledForcesPitchBlackSurfacesAcrossThemes() {
        WuppThemeMode.entries.forEach { mode ->
            val colors = colorsForTheme(
                mode = mode,
                accent = WuppAccentColor.OCEAN,
                pureBlackOled = true,
                customOutgoingBubbleArgb = 0xFF6B21A8L
            )
            assertTrue(colors.isDark)
            assertEquals(Color.Black, colors.appSurface)
            assertEquals(Color.Black, colors.chatCanvas)
            assertEquals(Color(0xFF6B21A8L), colors.outgoingBubble)
        }
    }

    @Test
    fun fontStyleFromStorageMigratesLegacySystemFontFlag() {
        assertEquals(WuppFontStyle.SYSTEM, WuppFontStyle.fromStorageKey(null, legacyUseSystemFont = true))
        assertEquals(WuppFontStyle.ROBO_CLEAN, WuppFontStyle.fromStorageKey(null, legacyUseSystemFont = false))
        assertEquals(WuppFontStyle.SERIF_EDITORIAL, WuppFontStyle.fromStorageKey("serif", legacyUseSystemFont = true))
    }

    @Test
    fun mixCallPcmSampleClampsSafelyWithoutOverflow() {
        assertEquals(Short.MAX_VALUE, mixCallPcmSample(25_000, 25_000))
        assertEquals(Short.MIN_VALUE, mixCallPcmSample(-25_000, -25_000))
        assertEquals(300.toShort(), mixCallPcmSample(100, 200))
    }

    @Test
    fun callRecordingWriterFinalizesValidRiffWaveFile() {
        val dir = createTempDirectory("wupp_call_rec_test").toFile()
        try {
            val out = File(dir, "test_call.wav")
            val writer = CallRecordingWriter(out, sampleRateHz = 16_000)
            val local = ShortArray(400) { 1000 }
            val remote = ShortArray(400) { 500 }
            writer.appendLocalFrame(local)
            writer.appendRemoteFrame(remote)
            val finished = writer.finish()
            assertNotNull(finished)
            val bytes = finished!!.readBytes()
            assertEquals(44 + 400 * 2, bytes.size)
            assertEquals("RIFF", String(bytes, 0, 4))
            assertEquals("WAVE", String(bytes, 8, 4))
            val le = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(36 + 800, le.getInt(4))
            assertEquals(800, le.getInt(40))
            assertEquals(1500.toShort(), le.getShort(44))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun buildMessageHistoryEntriesFiltersDeletedEditedAndOldRevisions() {
        val deletedMsg = MessageEntity(
            id = "m_del",
            chatJid = "111@s.whatsapp.net",
            senderJid = "111@s.whatsapp.net",
            senderName = "Alice",
            timestamp = 1000L,
            body = "Secret revoked note",
            isDeletedBySender = true,
            deletedAt = 5000L
        )
        val editedMsg = MessageEntity(
            id = "m_edit",
            chatJid = "222@s.whatsapp.net",
            senderJid = "222@s.whatsapp.net",
            senderName = "Bob",
            timestamp = 2000L,
            body = "Final revised schedule",
            isEdited = true,
            editedAt = 4000L
        )
        val edits = listOf(
            MessageEditEntity(
                id = 1L,
                messageId = "m_edit",
                oldText = "Original lunch meeting",
                editedAt = 3000L
            )
        )
        val chats = mapOf(
            "111@s.whatsapp.net" to "Alice",
            "222@s.whatsapp.net" to "Bob"
        )

        val allEntries = buildMessageHistoryEntries(
            deletedMessages = listOf(deletedMsg),
            editedMessages = listOf(editedMsg),
            editsByMessageId = edits.groupBy { it.messageId },
            filter = MessageHistoryFilter.ALL,
            query = "",
            chatNamesByJid = chats
        )
        assertEquals(2, allEntries.size)
        assertEquals("m_del", allEntries.first().message.id)

        val searchByOldRevision = buildMessageHistoryEntries(
            deletedMessages = listOf(deletedMsg),
            editedMessages = listOf(editedMsg),
            editsByMessageId = edits.groupBy { it.messageId },
            filter = MessageHistoryFilter.EDITED,
            query = "lunch",
            chatNamesByJid = chats
        )
        assertEquals(1, searchByOldRevision.size)
        assertEquals("m_edit", searchByOldRevision.first().message.id)
    }

    @Test
    fun sanitizeVaultFolderNameStripsTraversalAndIllegalChars() {
        assertEquals("etc_passwd", EagerMediaVault.sanitizeVaultFolderName("../etc/passwd"))
        assertEquals("Private Folder", EagerMediaVault.sanitizeVaultFolderName("   ...   "))
        assertEquals("Work_Photos", EagerMediaVault.sanitizeVaultFolderName("Work:Photos"))
    }

    @Test
    fun stickerCutoutEraseCheckRespectsColorTolerance() {
        assertTrue(shouldEraseBackgroundPixel(250, 248, 252, 255, 255, 255, tolerance = 20))
        assertFalse(shouldEraseBackgroundPixel(20, 180, 40, 255, 255, 255, tolerance = 35))
    }

    @Test
    fun resolveCanonicalDmJidMergesLidToPnAndFallsBackToPersistedCache() {
        val cache = mapOf("111222333@lid" to "919876543210@s.whatsapp.net")
        assertEquals(
            "919876543210@s.whatsapp.net",
            WaRealtimeService.resolveCanonicalDmJid("111222333@lid", "919876543210@s.whatsapp.net", cache)
        )
        assertEquals(
            "919876543210@s.whatsapp.net",
            WaRealtimeService.resolveCanonicalDmJid("919876543210@s.whatsapp.net", "111222333@lid", emptyMap())
        )
        assertEquals(
            "919876543210@s.whatsapp.net",
            WaRealtimeService.resolveCanonicalDmJid("111222333@lid", "", cache)
        )
        assertEquals(
            "120363000111222@g.us",
            WaRealtimeService.resolveCanonicalDmJid("120363000111222@g.us", "919876543210@s.whatsapp.net", cache)
        )
    }

    @Test
    fun shouldAutoMarkChatReadRequiresForegroundUnlockedAndMatchingChat() {
        val jid = "919876543210@s.whatsapp.net"
        assertTrue(WaRealtimeService.shouldAutoMarkChatRead(jid, jid, isUiForeground = true, isAppLocked = false))
        assertFalse(WaRealtimeService.shouldAutoMarkChatRead(jid, jid, isUiForeground = false, isAppLocked = false))
        assertFalse(WaRealtimeService.shouldAutoMarkChatRead(jid, jid, isUiForeground = true, isAppLocked = true))
        assertFalse(WaRealtimeService.shouldAutoMarkChatRead(null, jid, isUiForeground = true, isAppLocked = false))
        assertFalse(WaRealtimeService.shouldAutoMarkChatRead("111@s.whatsapp.net", jid, isUiForeground = true, isAppLocked = false))
    }

    @Test
    fun canonicalAndSelfChatJidHelpersMatchOwnAccount() {
        val own = "919876543210:12@s.whatsapp.net"
        assertEquals(
            "919876543210@s.whatsapp.net",
            com.wa.beastclient.domain.stealth.StealthMatrixManager.canonicalSelfChatJid(own)
        )
        assertTrue(
            com.wa.beastclient.domain.stealth.StealthMatrixManager.isSelfChatJid(
                "919876543210@s.whatsapp.net",
                own
            )
        )
        assertFalse(
            com.wa.beastclient.domain.stealth.StealthMatrixManager.isSelfChatJid(
                "919876543210@g.us",
                own
            )
        )
        assertFalse(
            com.wa.beastclient.domain.stealth.StealthMatrixManager.isSelfChatJid(
                "911234567890@s.whatsapp.net",
                own
            )
        )
    }

    @Test
    fun dynamicColorMappingUpdatesBackgroundsButtonsBubblesWithReadableContrast() {
        val fakeDynamicDark = androidx.compose.material3.darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFFB4C5FF),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF0E2978),
            primaryContainer = androidx.compose.ui.graphics.Color(0xFF284190),
            onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFDBE1FF),
            tertiary = androidx.compose.ui.graphics.Color(0xFFEFB8C8),
            background = androidx.compose.ui.graphics.Color(0xFF11131A),
            surface = androidx.compose.ui.graphics.Color(0xFF11131A),
            surfaceContainerLowest = androidx.compose.ui.graphics.Color(0xFF0C0E14),
            surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1D1F27),
            surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF272A32),
            onSurface = androidx.compose.ui.graphics.Color(0xFFE2E2EB),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC5C6D0),
            outlineVariant = androidx.compose.ui.graphics.Color(0xFF45464F),
            error = androidx.compose.ui.graphics.Color(0xFFFFB4AB)
        )
        val themed = com.wa.beastclient.ui.theme.colorsForTheme(
            mode = com.wa.beastclient.ui.theme.WuppThemeMode.DARK,
            dynamicScheme = fakeDynamicDark
        )
        assertEquals(fakeDynamicDark.background, themed.appSurface)
        assertEquals(fakeDynamicDark.surfaceContainerHigh, themed.elevatedBar)
        assertEquals(fakeDynamicDark.primary, themed.brightGreen)
        assertEquals(fakeDynamicDark.primaryContainer, themed.pillActiveBg)
        assertEquals(fakeDynamicDark.onPrimaryContainer, themed.pillActiveText)
        assertTrue(themed.accentGreen != com.wa.beastclient.ui.theme.WaDarkColors.accentGreen)
        assertTrue(themed.outgoingBubble != com.wa.beastclient.ui.theme.WaDarkColors.outgoingBubble)
    }

    @Test
    fun extractChatWallLinksNormalizesUrlsAndTrimsPunctuation() {
        val m1 = com.wa.beastclient.data.db.MessageEntity(
            id = "m1",
            chatJid = "123@s.whatsapp.net",
            senderJid = "123@s.whatsapp.net",
            senderName = "Alex",
            isFromMe = false,
            timestamp = 1000L,
            body = "Check https://example.com/docs?id=2, and www.github.com/hanzalaramz/Wupp!",
            msgType = "TEXT"
        )
        val links = com.wa.beastclient.ui.screens.extractChatWallLinks(listOf(m1))
        assertEquals(2, links.size)
        assertEquals("https://example.com/docs?id=2", links[0].openUrl)
        assertEquals("example.com", links[0].host)
        assertEquals("https://www.github.com/hanzalaramz/Wupp", links[1].openUrl)
        assertEquals("github.com", links[1].host)
    }
}
