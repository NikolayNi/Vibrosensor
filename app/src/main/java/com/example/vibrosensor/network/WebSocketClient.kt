package com.example.vibrosensor.network

import android.util.Log
import okhttp3.*
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject

class WebSocketClient(private val serverUrl: String) {

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var connected = false
    private var listener: ConnectionListener? = null

    interface ConnectionListener {
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
        fun onMessage(response: JSONObject)
    }

    fun setListener(listener: ConnectionListener) {
        this.listener = listener
    }

    fun connect() {
        client = OkHttpClient()
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
                connected = true
                listener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
                try {
                    val json = JSONObject(text)
                    listener?.onMessage(json)
                } catch (e: Exception) {
                    Log.e("WebSocket", "Parse error", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("WebSocket", "Received bytes: $bytes")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closed: $reason")
                connected = false
                listener?.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error", t)
                connected = false
                listener?.onError(t.message ?: "Unknown error")
            }
        })
    }

    fun sendCommand(command: String, callback: (JSONObject?) -> Unit) {
        if (!connected) {
            callback(null)
            return
        }

        try {
            val json = JSONObject()
            json.put("command", command)
            webSocket?.send(json.toString())
            Log.d("WebSocket", "Sent: $json")
            callback(JSONObject().put("status", "sent"))
        } catch (e: Exception) {
            Log.e("WebSocket", "Send error", e)
            callback(null)
        }
    }

    fun isConnected(): Boolean = connected

    fun close() {
        webSocket?.close(1000, "Closing")
        client?.dispatcher?.executorService?.shutdown()
    }
}