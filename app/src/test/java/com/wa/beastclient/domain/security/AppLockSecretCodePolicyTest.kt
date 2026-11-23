package com.wa.beastclient.domain.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSecretCodePolicyTest {
    @Test
    fun normalizeSecretCodeAutoPrefixesHashAndRejectsInvalidInputs() {
        assertEquals("#1234", AppLockManager.normalizeSecretCode("1234"))
        assertEquals("#1234", AppLockManager.normalizeSecretCode("  #1234  "))
        assertEquals("#secret", AppLockManager.normalizeSecretCode("secret"))
        assertNull(AppLockManager.normalizeSecretCode(""))
        assertNull(AppLockManager.normalizeSecretCode("#"))
        assertNull(AppLockManager.normalizeSecretCode("#1"))
        assertNull(AppLockManager.normalizeSecretCode("1"))
        assertNull(AppLockManager.normalizeSecretCode("#12 34"))
        assertNull(AppLockManager.normalizeSecretCode("#" + "a".repeat(32)))
    }

    @Test
    fun shouldHideLockedChatsAndHomeIndicatorsHideCompletelyWhenSecretCodeSet() {
        val secretOnlyCfg = AppLockConfig(
            mode = AppLockMode.OFF,
            hideLockedChatsBehindLock = false,
            hasSecretCode = true
        )
        assertTrue(AppLockManager.shouldHideLockedChats(secretOnlyCfg, revealed = false))
        assertFalse(AppLockManager.shouldShowLockedBannerInHome(secretOnlyCfg, revealed = false))

        assertFalse(AppLockManager.shouldHideLockedChats(secretOnlyCfg, revealed = true))
        assertTrue(AppLockManager.shouldShowLockedBannerInHome(secretOnlyCfg, revealed = true))
    }

    @Test
    fun shouldShowLockedBannerOnlyWhenNoSecretCodeAndHideBehindLockEnabled() {
        val lockWithoutSecret = AppLockConfig(
            mode = AppLockMode.DEVICE,
            hideLockedChatsBehindLock = true,
            hasSecretCode = false
        )
        assertTrue(AppLockManager.shouldHideLockedChats(lockWithoutSecret, revealed = false))
        assertTrue(AppLockManager.shouldShowLockedBannerInHome(lockWithoutSecret, revealed = false))
    }

    @Test
    fun shouldSuppressAlertsForChatSilencesNotificationsAndCallsOnlyWhileHidden() {
        val cfg = AppLockConfig(
            mode = AppLockMode.OFF,
            hideLockedChatsBehindLock = true,
            hasSecretCode = true
        )
        assertTrue(AppLockManager.shouldSuppressAlertsForChat(chatIsLocked = true, cfg = cfg, revealed = false))
        assertFalse(AppLockManager.shouldSuppressAlertsForChat(chatIsLocked = false, cfg = cfg, revealed = false))
        assertFalse(AppLockManager.shouldSuppressAlertsForChat(chatIsLocked = true, cfg = cfg, revealed = true))
    }
}
