package com.wa.beastclient.ui.components

import com.wa.beastclient.data.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContactSharingHelperTest {

    @Test
    fun buildVCard_withNameAndPhone_formatsCorrectVCard() {
        val vcard = ContactSharingHelper.buildVCard("Alice Smith", listOf("+1234567890" to "Mobile"))
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("VERSION:3.0"))
        assertTrue(vcard.contains("FN:Alice Smith"))
        assertTrue(vcard.contains("N:Smith;Alice;;;"))
        assertTrue(vcard.contains("TEL;TYPE=CELL:+1234567890"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun buildVCard_withMultiplePhonesAndTypes_formatsAllPhones() {
        val vcard = ContactSharingHelper.buildVCard(
            "Dr. John Doe",
            listOf("+111" to "Home", "+222" to "Work", "+333" to "Mobile")
        )
        assertTrue(vcard.contains("FN:Dr. John Doe"))
        assertTrue(vcard.contains("TEL;TYPE=HOME:+111"))
        assertTrue(vcard.contains("TEL;TYPE=WORK:+222"))
        assertTrue(vcard.contains("TEL;TYPE=CELL:+333"))
    }

    @Test
    fun buildVCard_withoutPhone_omitsTelField() {
        val vcard = ContactSharingHelper.buildVCard("Bob Jones", emptyList())
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("FN:Bob Jones"))
        assertTrue(!vcard.contains("TEL;"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun resolveContactNameAndPhone_handlesNewlineBody() {
        val msg = MessageEntity(
            id = "1", chatJid = "1@s.whatsapp.net", senderJid = "1@s.whatsapp.net",
            timestamp = 1000L, body = "Jane Doe\n+9876543210", msgType = "CONTACT"
        )
        val (name, phone) = resolveContactNameAndPhone(msg)
        assertEquals("Jane Doe", name)
        assertEquals("+9876543210", phone)
    }

    @Test
    fun resolveContactNameAndPhone_readsFromVCardFile() {
        val vcfFile = File.createTempFile("test_contact", ".vcf").apply {
            writeText("BEGIN:VCARD\nVERSION:3.0\nFN:Carlos\nTEL;TYPE=CELL:+551199999\nEND:VCARD\n")
            deleteOnExit()
        }
        val msg = MessageEntity(
            id = "2", chatJid = "2@s.whatsapp.net", senderJid = "2@s.whatsapp.net",
            timestamp = 1000L, body = "Carlos", msgType = "CONTACT",
            localMediaPath = vcfFile.absolutePath
        )
        val (name, phone) = resolveContactNameAndPhone(msg)
        assertEquals("Carlos", name)
        assertEquals("+551199999", phone)
    }
}
