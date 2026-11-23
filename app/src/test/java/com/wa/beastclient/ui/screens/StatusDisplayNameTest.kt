package com.wa.beastclient.ui.screens

import com.wa.beastclient.data.db.ChatEntity
import com.wa.beastclient.data.db.ContactEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusDisplayNameTest {
    @Test
    fun resolveStatusDisplayNamePrefersSavedContactWhenRequestedOrChatMissing() {
        val jid = "923001234567@s.whatsapp.net"
        val chat = ChatEntity(jid = jid, name = "😁")
        val contact = ContactEntity(jid = jid, name = "Sara", isSaved = true)

        assertEquals(
            "Sara",
            resolveStatusDisplayName(
                senderJid = jid,
                storedName = "😁",
                senderChat = null,
                senderContact = contact,
                preferPhonebookNames = false
            )
        )
        assertEquals(
            "Sara",
            resolveStatusDisplayName(
                senderJid = jid,
                storedName = "😁",
                senderChat = chat,
                senderContact = contact,
                preferPhonebookNames = true
            )
        )
    }
}
