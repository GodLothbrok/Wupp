package com.wa.beastclient.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineReadySessionPolicyTest {

    @Test
    fun engineReadyWithSavedSessionStaysLoggedInOnline() {
        val status = resolveEngineReadyBridgeStatus(
            current = BridgeStatus(),
            eventLoggedIn = true,
            eventOwnJid = "923001234567@s.whatsapp.net",
            nativeLoggedIn = true,
            nativeOwnJid = "923001234567@s.whatsapp.net",
            offlineMode = false
        )
        assertTrue(status.engineReady)
        assertTrue(status.isLoggedIn)
        assertEquals("923001234567@s.whatsapp.net", status.ownJid)
        assertEquals("CONNECTING", status.connectionState)
    }

    @Test
    fun engineReadyWithOfflineModeDoesNotDropLoggedInState() {
        val status = resolveEngineReadyBridgeStatus(
            current = BridgeStatus(engineReady = true, isLoggedIn = true, connectionState = "OFFLINE_PAUSED"),
            eventLoggedIn = false,
            eventOwnJid = "",
            nativeLoggedIn = true,
            nativeOwnJid = "923001234567@s.whatsapp.net",
            offlineMode = true
        )
        assertTrue(status.engineReady)
        assertTrue(status.isLoggedIn)
        assertEquals("923001234567@s.whatsapp.net", status.ownJid)
        assertEquals("OFFLINE_PAUSED", status.connectionState)
    }

    @Test
    fun engineReadyWithoutSessionEntersAwaitingPair() {
        val status = resolveEngineReadyBridgeStatus(
            current = BridgeStatus(),
            eventLoggedIn = false,
            eventOwnJid = "",
            nativeLoggedIn = false,
            nativeOwnJid = "",
            offlineMode = true
        )
        assertTrue(status.engineReady)
        assertFalse(status.isLoggedIn)
        assertEquals("", status.ownJid)
        assertEquals("AWAITING_PAIR", status.connectionState)
    }
}
