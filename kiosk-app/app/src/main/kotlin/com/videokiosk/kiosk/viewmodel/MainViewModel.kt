package com.videokiosk.kiosk.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videokiosk.kiosk.model.CallState
import com.videokiosk.kiosk.service.SignalingClient
import com.videokiosk.kiosk.service.WebRTCClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel for the main kiosk screen.
 * Manages the call lifecycle by coordinating [SignalingClient] and [WebRTCClient].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState

    // ---------------------------------------------------------------------------
    // Services (initialized lazily via initialize())
    // ---------------------------------------------------------------------------

    private lateinit var signalingClient: SignalingClient
    private lateinit var webRTCClient: WebRTCClient

    /** Unique identifier for this kiosk device. */
    private var kioskId: String = "kiosk_1"

    // ---------------------------------------------------------------------------
    // Initialization
    // ---------------------------------------------------------------------------

    /**
     * Set up the signaling and WebRTC clients.
     * Call this once from [com.videokiosk.kiosk.MainActivity] after reading
     * the server URL from SharedPreferences.
     *
     * @param serverUrl WebSocket URL of the signaling server, e.g. "ws://192.168.1.10:8080"
     * @param kioskId   Unique identifier for this kiosk (default "kiosk_1")
     */
    fun initialize(serverUrl: String, kioskId: String = "kiosk_1") {
        this.kioskId = kioskId

        // TODO: instantiate WebRTCClient with ICE server config from preferences
        webRTCClient = WebRTCClient()

        signalingClient = SignalingClient(object : SignalingClient.SignalingListener {
            override fun onMessage(message: JSONObject) {
                onSignalingMessage(message)
            }

            override fun onConnected() {
                // TODO: update a connection status indicator in the UI
                println("[MainViewModel] Connected to signaling server")
            }

            override fun onDisconnected() {
                _callState.value = CallState.Error("Потеряно соединение с сервером")
            }

            override fun onError(error: String) {
                _callState.value = CallState.Error(error)
            }
        })

        signalingClient.connect("$serverUrl?role=client&id=$kioskId")
    }

    // ---------------------------------------------------------------------------
    // Public actions
    // ---------------------------------------------------------------------------

    /**
     * Initiate a call to the operator.
     * Sends a `call` message to the signaling server.
     */
    fun startCall() {
        if (_callState.value !is CallState.Idle) return

        _callState.value = CallState.Calling

        val msg = JSONObject().apply {
            put("type", "call")
            put("clientId", kioskId)
        }
        signalingClient.send(msg)
    }

    /**
     * Terminate the current call or cancel a queued/calling request.
     */
    fun endCall() {
        viewModelScope.launch {
            webRTCClient.close()
            // TODO: send "end_call" message to signaling server
            _callState.value = CallState.Idle
        }
    }

    // ---------------------------------------------------------------------------
    // Signaling message handler
    // ---------------------------------------------------------------------------

    /**
     * Called whenever a JSON message arrives from the signaling server.
     * Routes messages to the correct handler based on the `type` field.
     */
    fun onSignalingMessage(message: JSONObject) {
        when (val type = message.optString("type")) {
            "queued" -> {
                val position = message.optInt("position", 1)
                _callState.value = CallState.Queued(position)
            }

            "accept" -> {
                _callState.value = CallState.InCall
                // TODO: webRTCClient.createOffer() and send via signalingClient
            }

            "reject" -> {
                _callState.value = CallState.Idle
            }

            "offer" -> {
                val sdp = message.optString("sdp")
                // TODO: webRTCClient.handleOffer(sdp)
                // TODO: create answer and send back via signalingClient
            }

            "answer" -> {
                val sdp = message.optString("sdp")
                // TODO: webRTCClient.handleAnswer(sdp)
            }

            "ice_candidate" -> {
                val ice = message.optJSONObject("ice")
                if (ice != null) {
                    // TODO: webRTCClient.addIceCandidate(ice)
                }
            }

            else -> println("[MainViewModel] Unhandled message type: $type")
        }
    }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        if (::signalingClient.isInitialized) {
            signalingClient.disconnect()
        }
        if (::webRTCClient.isInitialized) {
            webRTCClient.close()
        }
    }
}
