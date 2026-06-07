package com.videokiosk.kiosk.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videokiosk.kiosk.model.CallState
import com.videokiosk.kiosk.service.SignalingClient
import com.videokiosk.kiosk.service.WebRTCClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer

/**
 * ViewModel for the main kiosk screen.
 * Manages the call lifecycle by coordinating [SignalingClient] and [WebRTCClient].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainViewModel"

        // Public STUN — works on any LAN without a TURN server
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState

    // -------------------------------------------------------------------------
    // Services
    // -------------------------------------------------------------------------

    private lateinit var signalingClient: SignalingClient
    private lateinit var webRTCClient: WebRTCClient

    private var kioskId: String = "kiosk_1"
    private var serverUrl: String = ""
    private var webRtcReady = false
    // Prevents duplicate initialization if Activity is recreated while ViewModel survives
    private var initialized = false

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    fun initialize(serverUrl: String, kioskId: String = "kiosk_1") {
        if (initialized) {
            Log.i(TAG, "Already initialized — skipping duplicate initialize() call")
            return
        }
        initialized = true
        this.kioskId = kioskId
        this.serverUrl = serverUrl
        Log.i(TAG, "Initializing: serverUrl=$serverUrl kioskId=$kioskId")

        webRTCClient = WebRTCClient()

        signalingClient = SignalingClient(object : SignalingClient.SignalingListener {
            override fun onMessage(message: JSONObject) = onSignalingMessage(message)

            override fun onConnected() {
                Log.i(TAG, "Connected to signaling server — state → Idle")
                // Recover from Error state (e.g., after a reconnect)
                if (_callState.value is CallState.Error) {
                    _callState.value = CallState.Idle
                }
            }

            override fun onDisconnected() {
                Log.w(TAG, "Disconnected from signaling server — scheduling reconnect")
                // Only show error if we were in an active state (not if already tearing down)
                if (_callState.value !is CallState.Error) {
                    _callState.value = CallState.Error("Переподключение...")
                }
                scheduleReconnect()
            }

            override fun onError(error: String) {
                Log.e(TAG, "SignalingClient error: $error — scheduling reconnect")
                _callState.value = CallState.Error("Ошибка: $error")
                scheduleReconnect()
            }
        })

        signalingClient.connect("$serverUrl?role=client&id=$kioskId")
    }

    // -------------------------------------------------------------------------
    // Public actions
    // -------------------------------------------------------------------------

    fun startCall() {
        val current = _callState.value
        if (current !is CallState.Idle && current !is CallState.Error) {
            Log.w(TAG, "startCall ignored — state=$current")
            return
        }
        if (!signalingClient.isConnected()) {
            Log.w(TAG, "startCall: not connected yet — ignored")
            return
        }
        Log.i(TAG, "Starting call from kioskId=$kioskId")
        _callState.value = CallState.Calling

        signalingClient.send(JSONObject().apply {
            put("type", "call")
            put("clientId", kioskId)
        })
    }

    // -------------------------------------------------------------------------
    // Auto-reconnect
    // -------------------------------------------------------------------------

    private fun scheduleReconnect() {
        viewModelScope.launch {
            delay(2_000)
            if (!signalingClient.isConnected()) {
                Log.i(TAG, "Reconnecting to signaling server: $serverUrl")
                signalingClient.connect("$serverUrl?role=client&id=$kioskId")
            } else {
                Log.d(TAG, "scheduleReconnect: already connected — skipping")
            }
        }
    }

    /**
     * Expose the WebRTC EGL context so CallFragment can initialise SurfaceViewRenderers
     * with a compatible context.
     */
    fun getWebRTCEglContext(): EglBase.Context? =
        if (::webRTCClient.isInitialized) webRTCClient.getEglBaseContext() else null

    /**
     * Wire SurfaceViewRenderers from CallFragment to the WebRTC engine.
     * Safe to call before or after remote track arrives.
     */
    fun attachCallRenderers(localRenderer: SurfaceViewRenderer, remoteRenderer: SurfaceViewRenderer) {
        if (::webRTCClient.isInitialized) {
            webRTCClient.attachLocalRenderer(localRenderer)
            webRTCClient.attachRemoteRenderer(remoteRenderer)
            Log.i(TAG, "Call renderers attached")
        } else {
            Log.w(TAG, "attachCallRenderers: webRTCClient not yet initialized")
        }
    }

    fun endCall() {
        Log.i(TAG, "Ending call — state=${_callState.value}")
        viewModelScope.launch {
            webRTCClient.close()
            webRtcReady = false
            signalingClient.send(JSONObject().apply {
                put("type", "end_call")
                put("clientId", kioskId)
                put("target", "operator")
            })
            _callState.value = CallState.Idle
            Log.i(TAG, "Call ended — state → Idle")
        }
    }

    // -------------------------------------------------------------------------
    // Signaling message handler
    // -------------------------------------------------------------------------

    fun onSignalingMessage(message: JSONObject) {
        val type = message.optString("type")
        Log.d(TAG, "Signaling message: type=$type")

        when (type) {
            "queued" -> {
                val position = message.optInt("position", 1)
                Log.i(TAG, "Queued at position $position")
                _callState.value = CallState.Queued(position)
            }

            "accept" -> {
                Log.i(TAG, "Call accepted — initialising WebRTC and creating offer")
                _callState.value = CallState.InCall
                initWebRTCAndOffer()
            }

            "reject" -> {
                Log.i(TAG, "Call rejected — returning to Idle")
                _callState.value = CallState.Idle
            }

            "answer" -> {
                val sdp = message.optString("sdp")
                Log.i(TAG, "Remote SDP answer received (length=${sdp.length})")
                webRTCClient.handleAnswer(sdp)
            }

            "ice_candidate" -> {
                val ice = message.optJSONObject("ice")
                if (ice != null) {
                    Log.d(TAG, "Remote ICE candidate: mid=${ice.optString("sdpMid")}")
                    webRTCClient.addIceCandidate(ice)
                } else {
                    Log.w(TAG, "ice_candidate message missing 'ice' object")
                }
            }

            "end_call" -> {
                Log.i(TAG, "Operator ended call")
                viewModelScope.launch {
                    webRTCClient.close()
                    webRtcReady = false
                    _callState.value = CallState.Idle
                }
            }

            else -> Log.w(TAG, "Unhandled signaling message type: $type")
        }
    }

    // -------------------------------------------------------------------------
    // WebRTC setup — called once operator accepts
    // -------------------------------------------------------------------------

    private fun initWebRTCAndOffer() {
        if (webRtcReady) {
            Log.w(TAG, "WebRTC already initialised — skipping")
            return
        }

        // Wire the WebRTC listener BEFORE initialising so no events are missed
        webRTCClient.setListener(object : WebRTCClient.WebRTCListener {

            override fun onLocalSdpReady(type: String, sdp: String) {
                Log.i(TAG, "Local SDP ready: type=$type, sending to operator")
                signalingClient.send(JSONObject().apply {
                    put("type", type)          // "offer"
                    put("sdp", sdp)
                    put("target", "operator")
                    put("clientId", kioskId)
                })
            }

            override fun onIceCandidateGenerated(candidate: IceCandidate) {
                Log.d(TAG, "ICE candidate → operator: mid=${candidate.sdpMid}")
                signalingClient.send(JSONObject().apply {
                    put("type", "ice_candidate")
                    put("target", "operator")
                    put("clientId", kioskId)
                    put("ice", JSONObject().apply {
                        put("sdpMid", candidate.sdpMid)
                        put("sdpMLineIndex", candidate.sdpMLineIndex)
                        put("candidate", candidate.sdp)
                    })
                })
            }

            override fun onCallEnded() {
                Log.i(TAG, "WebRTC connection ended")
                viewModelScope.launch { _callState.value = CallState.Idle }
            }

            override fun onError(message: String) {
                Log.e(TAG, "WebRTC error: $message")
                _callState.value = CallState.Error(message)
            }
        })

        // Initialise factory and peer connection
        webRTCClient.initialize(getApplication(), ICE_SERVERS)
        webRTCClient.createPeerConnection(ICE_SERVERS)
        webRtcReady = true

        // Add camera + microphone tracks BEFORE creating the offer so SDP has sendrecv
        Log.i(TAG, "Starting local capture (camera + mic)")
        webRTCClient.startLocalCapture(getApplication())

        Log.i(TAG, "WebRTC initialised — creating offer")
        webRTCClient.createOffer()
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCleared() {
        super.onCleared()
        Log.i(TAG, "ViewModel cleared — releasing resources")
        if (::signalingClient.isInitialized) signalingClient.disconnect()
        if (::webRTCClient.isInitialized) webRTCClient.close()
    }
}
