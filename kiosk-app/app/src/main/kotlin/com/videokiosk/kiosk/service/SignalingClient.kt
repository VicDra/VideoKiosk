package com.videokiosk.kiosk.service

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * OkHttp-based WebSocket client for communicating with the Node.js signaling server.
 *
 * Each call to [connect] bumps a generation counter so that stale close/failure callbacks
 * from the previous WebSocket are silently discarded — this prevents phantom disconnects
 * when [connect] is called again during auto-reconnect while the old socket's close event
 * is still in-flight.
 */
class SignalingClient(private val listener: SignalingListener) {

    companion object {
        private const val TAG = "SignalingClient"
    }

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
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    @Volatile
    private var isConnected = false

    // Incremented on every connect() call; each WebSocketListener captures its own
    // generation value so stale callbacks from old sockets can be detected and ignored.
    private val generation = AtomicInteger(0)

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Open a WebSocket connection to the signaling server.
     * Safe to call again for reconnecting — events from the previous socket are discarded.
     */
    fun connect(url: String) {
        Log.i(TAG, "Connecting to: $url")
        // Do NOT cancel the existing socket here.
        // When the new socket connects, the server will send 1001 to the old one,
        // which the old generation's listener will silently ignore via isCurrent().
        // Cancelling the old socket creates a race where both sockets are closing
        // and opening simultaneously, causing the server to see a duplicate and
        // send 1001 to the NEW socket instead.
        isConnected = false

        val myGen = generation.incrementAndGet()
        val request = Request.Builder().url(url).build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            /** Returns true if this listener is still "current" (not superseded by a newer connect()). */
            private fun isCurrent() = (myGen == generation.get())

            override fun onOpen(ws: WebSocket, response: Response) {
                if (!isCurrent()) return
                isConnected = true
                Log.i(TAG, "WebSocket opened (HTTP ${response.code}) gen=$myGen")
                listener.onConnected()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (!isCurrent()) return
                Log.d(TAG, "Received: $text")
                try {
                    listener.onMessage(JSONObject(text))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse message: ${e.message} — raw=$text")
                    listener.onError("Failed to parse message: ${e.message}")
                }
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                if (!isCurrent()) return
                onMessage(ws, bytes.utf8())
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                if (!isCurrent()) {
                    // Stale close from a superseded socket — just ack it
                    ws.close(1000, null)
                    return
                }
                Log.i(TAG, "WebSocket closing: code=$code reason=$reason gen=$myGen")
                ws.close(1000, null)
                isConnected = false
                // onClosed will fire next and call onDisconnected
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (!isCurrent()) {
                    Log.d(TAG, "Stale onClosed gen=$myGen (current=${generation.get()}) — ignored")
                    return
                }
                Log.i(TAG, "WebSocket closed: code=$code reason=$reason gen=$myGen")
                isConnected = false
                listener.onDisconnected()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent()) return
                Log.e(TAG, "WebSocket failure: ${t.message} (response=${response?.code})", t)
                isConnected = false
                listener.onError(t.message ?: "Unknown WebSocket error")
            }
        })
    }

    /**
     * Send a JSON message to the signaling server.
     */
    fun send(message: JSONObject) {
        if (isConnected) {
            Log.d(TAG, "Sending: $message")
            webSocket?.send(message.toString())
        } else {
            Log.w(TAG, "Cannot send — not connected. Dropped: $message")
        }
    }

    /**
     * Close the WebSocket connection gracefully and cancel any pending reconnects.
     */
    fun disconnect() {
        Log.i(TAG, "Disconnecting")
        generation.incrementAndGet()  // Invalidate any in-flight callbacks
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}
