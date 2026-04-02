package com.vindmijnmobiel

import fi.iki.elonen.NanoHTTPD

class PhoneRingServer(port: Int, private val player: RingPlayer) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val response = when (session.uri) {
            "/ring" -> {
                player.startRinging()
                newFixedLengthResponse("OK")
            }
            "/stop" -> {
                player.stopRinging()
                newFixedLengthResponse("OK")
            }
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found"
            )
        }
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }
}
