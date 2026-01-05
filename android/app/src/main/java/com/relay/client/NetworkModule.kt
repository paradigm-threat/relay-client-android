package com.relay.client

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

object NetworkModule {
    fun checkPort(host: String, port: Int): Boolean {
        return try {
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), 500)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
