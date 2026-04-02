package com.vindmijnmobiel

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class PhoneRingServerTest {

    class FakeRingPlayer : RingPlayer {
        var started = false
        var stopped = false
        override val isRinging: Boolean get() = started && !stopped
        override fun startRinging() { started = true }
        override fun stopRinging() { stopped = true }
    }

    private lateinit var fakePlayer: FakeRingPlayer
    private lateinit var server: PhoneRingServer

    @Before
    fun setUp() {
        fakePlayer = FakeRingPlayer()
        server = PhoneRingServer(15000, fakePlayer)
        server.start()
        Thread.sleep(100)
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun get(path: String): Pair<Int, String> {
        val conn = URL("http://localhost:15000$path").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return code to body
    }

    @Test
    fun `GET ring calls startRinging and returns 200 OK`() {
        val (code, body) = get("/ring")
        assertEquals(200, code)
        assertEquals("OK", body)
        assertTrue(fakePlayer.started)
    }

    @Test
    fun `GET stop calls stopRinging and returns 200 OK`() {
        val (code, body) = get("/stop")
        assertEquals(200, code)
        assertEquals("OK", body)
        assertTrue(fakePlayer.stopped)
    }

    @Test
    fun `GET ring response includes CORS header`() {
        val conn = URL("http://localhost:15000/ring").openConnection() as HttpURLConnection
        conn.connect()
        val cors = conn.getHeaderField("Access-Control-Allow-Origin")
        conn.disconnect()
        assertEquals("*", cors)
    }

    @Test
    fun `unknown path returns 404`() {
        val conn = URL("http://localhost:15000/unknown").openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }
}
