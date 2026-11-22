package com.wa.beastclient.domain.accounts

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountManagerTest {

    @Test
    fun `sanitizeAccountId normalizes invalid chars and defaults blanks`() {
        assertEquals(AccountManager.DEFAULT_ACCOUNT_ID, sanitizeAccountId(""))
        assertEquals(AccountManager.DEFAULT_ACCOUNT_ID, sanitizeAccountId("   "))
        assertEquals("acc_2", sanitizeAccountId("acc_2"))
        assertEquals("work_phone-1", sanitizeAccountId(" Work Phone-1 "))
        assertEquals("user_dot_net", sanitizeAccountId("user.dot@net"))
    }

    @Test
    fun `scopedPrefsName preserves legacy root names for default account`() {
        assertEquals("wupp_stealth_matrix", scopedPrefsName("wupp_stealth_matrix", AccountManager.DEFAULT_ACCOUNT_ID))
        assertEquals("wupp_stealth_matrix", scopedPrefsName("wupp_stealth_matrix", "   "))
        assertEquals("wupp_stealth_matrix__acct_acc_2", scopedPrefsName("wupp_stealth_matrix", "acc_2"))
        assertEquals("wupp_jid_aliases__acct_work_1", scopedPrefsName("wupp_jid_aliases", "Work/1"))
    }

    @Test
    fun `wuppAccount formats displayPhone and displayTitle cleanly`() {
        val full = WuppAccount(
            id = "acc_2",
            label = "Work",
            ownJid = "15551234567:42@s.whatsapp.net",
            pushName = "Alice"
        )
        assertEquals("+15551234567", full.displayPhone)
        assertEquals("Work", full.displayTitle)

        val fallbackTitle = WuppAccount(
            id = "acc_3",
            label = "",
            ownJid = "447700900123@s.whatsapp.net",
            pushName = ""
        )
        assertEquals("+447700900123", fallbackTitle.displayPhone)
        assertEquals("+447700900123", fallbackTitle.displayTitle)

        val unlinked = WuppAccount(id = "acc_4", label = "", ownJid = "", pushName = "")
        assertEquals("", unlinked.displayPhone)
        assertEquals("Account", unlinked.displayTitle)
    }
}
