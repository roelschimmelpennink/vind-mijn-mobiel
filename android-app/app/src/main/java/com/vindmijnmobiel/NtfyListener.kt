package com.vindmijnmobiel

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class NtfyListener(
    private val topic: String,
    private val player: RingPlayer
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        running = true
        thread = Thread {
            while (running) {
                try {
                    connect()
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    if (running) Thread.sleep(5000)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun connect() {
        val conn = URL("https://ntfy.sh/$topic/sse").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 0
        try {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            var line: String?
            while (running && reader.readLine().also { line = it } != null) {
                handleLine(line!!, player)
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun handleLine(line: String, player: RingPlayer) {
            if (!line.startsWith("data:")) return
            val data = line.removePrefix("data:").trim()
            when {
                data.contains("\"ring\"") -> player.startRinging()
                data.contains("\"stop\"") -> player.stopRinging()
            }
        }
    }
}
