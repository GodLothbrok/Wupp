package com.wa.beastclient.domain.stealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Error463GuardPolicyTest {

    @Test
    fun savedContactsAndPhonebookContactsAreAlwaysAllowedWithoutColdStartPrompt() {
        assertTrue(
            StealthMatrixManager.isKnownOrPriorHistoryChat(
                chatJid = "15338623410@s.whatsapp.net",
                isContactSaved = true
            )
        )
        assertTrue(
            StealthMatrixManager.isKnownOrPriorHistoryChat(
                chatJid = "15338623410@s.whatsapp.net",
                isPhonebookSaved = true
            )
        )
    }

    @Test
    fun aliasHistoryAndChatTimestampAllowOutboundWithoutPrompt() {
        assertTrue(
            StealthMatrixManager.isKnownOrPriorHistoryChat(
                chatJid = "15338623410@s.whatsapp.net",
                aliasMessageCount = 3
            )
        )
        assertTrue(
            StealthMatrixManager.isKnownOrPriorHistoryChat(
                chatJid = "15338623410@s.whatsapp.net",
                hasChatHistoryTimestamp = true
            )
        )
    }

    @Test
    fun groupsChannelsBroadcastAndSelfChatBypassColdStartGuard() {
        assertTrue(StealthMatrixManager.isKnownOrPriorHistoryChat(chatJid = "120363000111@g.us"))
        assertTrue(StealthMatrixManager.isKnownOrPriorHistoryChat(chatJid = "120363000222@newsletter"))
        assertTrue(StealthMatrixManager.isKnownOrPriorHistoryChat(chatJid = "status@broadcast"))
        assertTrue(
            StealthMatrixManager.isKnownOrPriorHistoryChat(
                chatJid = "923001111111@s.whatsapp.net",
                ownJid = "923001111111@s.whatsapp.net"
            )
        )
    }

    @Test
    fun genuinelyUnsavedZeroHistoryNumberRequiresConfirmationWarning() {
        assertFalse(
            StealthMatrixManager.isKnownOrPriorHistoryChat(
                chatJid = "15305234187@s.whatsapp.net",
                ownJid = "923001111111@s.whatsapp.net",
                messageCount = 0,
                aliasMessageCount = 0,
                hasChatHistoryTimestamp = false,
                isContactSaved = false,
                isPhonebookSaved = false
            )
        )
    }
}
