package com.videokiosk.kiosk.service

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based WebSocket client for communicating with the Node.js signaling server.
 */
class SignalingClient(private val listener: SignalingListener) {

    // ---------------------------------------------------------------------------
    // Listener interface
    // ---------------------------------------------------------------------------

    interface SignalingListener {
        fun onMessage(message: JSONObject)
        fun onConnected()
        fun onDisconnected()
        fun onError(error: String)
    }

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // no timeout for WebSocket reads
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Open a WebSocket connection to the signaling server.
     *
     * @param url Full WebSocket URL including query params,
     *            e.g. "ws://192.168.1.10:8080?role=client&id=kiosk_1"
     */
    fun connect(url: String) {
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    listener.onMessage(msg)
                } catch (e: Exception) {
                    listener.onError("Failed to parse message: ${e.message}")
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                // TODO: handle binary frames if needed
                onMessage(ws, bytes.utf8())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                isConnected = false
                listener.onDisconnected()
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                isConnected = false
                listener.onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                listener.onError(t.message ?: "Unknown WebSocket error")
            }
        })
    }

    /**
     * Send a JSON message to the signaling server.
     *
     * @param message The JSON object to serialize and send.
     */
    fun send(message: JSONObject) {
        if (webSocket != null && isConnected) {
            webSocket?.send(message.toString())
        } else {
            // TODO: queue messages for reconnection
            println("[SignalingClient] Cannot send — not connected")
        }
    }

    /**
     * Close the WebSocket connection gracefully.
     */
    fun disconnect() {
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}
