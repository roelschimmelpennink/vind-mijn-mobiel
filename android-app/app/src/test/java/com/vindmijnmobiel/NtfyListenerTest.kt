package com.vindmijnmobiel

import org.junit.Assert.*
import org.junit.Test

class NtfyListenerTest {

    class FakeRingPlayer : RingPlayer {
        var started = false
        var stopped = false
        override val isRinging: Boolean get() = started && !stopped
        override fun startRinging() { started = true }
        override fun stopRinging() { stopped = true }
    }

    @Test
    fun `handleLine ring JSON calls startRinging`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("""data: {"id":"abc","message":"ring"}""", player)
        assertTrue(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine stop JSON calls stopRinging`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("""data: {"id":"abc","message":"stop"}""", player)
        assertTrue(player.stopped)
        assertFalse(player.started)
    }

    @Test
    fun `handleLine event line is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("event: message", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine keepalive line is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine(": keepalive", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine empty line is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine unrecognized data is ignored`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("""data: {"id":"abc","message":"hello"}""", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }

    @Test
    fun `handleLine value ringtone does not trigger ring command`() {
        val player = FakeRingPlayer()
        NtfyListener.handleLine("""data: {"id":"abc","message":"ringtone"}""", player)
        assertFalse(player.started)
        assertFalse(player.stopped)
    }
}
