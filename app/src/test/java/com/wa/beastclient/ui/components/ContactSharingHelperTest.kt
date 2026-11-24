package com.wa.beastclient.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactSharingHelperTest {

    @Test
    fun buildVCard_withNameAndPhone_formatsCorrectVCard() {
        val vcard = ContactSharingHelper.buildVCard("Alice Smith", "+1234567890")
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("VERSION:3.0"))
        assertTrue(vcard.contains("FN:Alice Smith"))
        assertTrue(vcard.contains("TEL;TYPE=CELL:+1234567890"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun buildVCard_withoutPhone_omitsTelField() {
        val vcard = ContactSharingHelper.buildVCard("Bob Jones", null)
        assertTrue(vcard.contains("BEGIN:VCARD"))
        assertTrue(vcard.contains("FN:Bob Jones"))
        assertTrue(!vcard.contains("TEL;"))
        assertTrue(vcard.contains("END:VCARD"))
    }

    @Test
    fun buildVCard_withBlankPhone_omitsTelField() {
        val vcard = ContactSharingHelper.buildVCard("Charlie", "   ")
        assertTrue(vcard.contains("FN:Charlie"))
        assertTrue(!vcard.contains("TEL;"))
    }
}
