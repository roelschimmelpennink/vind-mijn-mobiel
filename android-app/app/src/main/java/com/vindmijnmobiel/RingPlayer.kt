package com.vindmijnmobiel

interface RingPlayer {
    fun startRinging()
    fun stopRinging()
    val isRinging: Boolean
}
