package com.videokiosk.kiosk.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * WebRTC client for the kiosk Android application.
 *
 * Lifecycle:
 *  1. initialize(context, iceServers)    — create PeerConnectionFactory + EglBase
 *  2. createPeerConnection(iceServers)   — create RTCPeerConnection with observer
 *  3. startLocalCapture(context)         — open camera/mic, add tracks to peer connection
 *  4. createOffer()                      — create SDP offer (now contains sendrecv tracks)
 *  5. [after CallFragment is shown]
 *     attachLocalRenderer(renderer)      — show own camera preview
 *     attachRemoteRenderer(renderer)     — show remote video
 */
class WebRTCClient {

    companion object {
        private const val TAG = "WebRTCClient"
        private const val AUDIO_TRACK_ID = "kiosk_audio"
        private const val VIDEO_TRACK_ID = "kiosk_video"
        private const val STREAM_ID = "kiosk_stream"
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_FPS = 30
    }

    // ---------------------------------------------------------------------------
    // Listener interface
    // ---------------------------------------------------------------------------

    interface WebRTCListener {
        fun onLocalSdpReady(type: String, sdp: String)
        fun onIceCandidateGenerated(candidate: IceCandidate)
        fun onCallEnded()
        fun onError(message: String)
    }

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    // Remote video track received via onAddTrack
    private var remoteVideoTrack: VideoTrack? = null

    // Renderers set from CallFragment — may arrive before or after remote track
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var listener: WebRTCListener? = null

    // Handler for ICE DISCONNECTED → timeout → end call
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iceDisconnectTimeoutMs = 10_000L
    private val iceDisconnectRunnable = Runnable {
        Log.w(TAG, "ICE DISCONNECTED timeout (${iceDisconnectTimeoutMs}ms) — ending call")
        listener?.onCallEnded()
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    fun setListener(listener: WebRTCListener) {
        this.listener = listener
    }

    /**
     * Expose the EGL context so CallFragment can initialise SurfaceViewRenderers
     * with the same context used by the capturer, ensuring GL compatibility.
     */
    fun getEglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    /**
     * Step 1 — initialise the WebRTC stack.
     */
    fun initialize(context: Context, iceServers: List<PeerConnection.IceServer>) {
        Log.i(TAG, "Initializing WebRTC stack")
        eglBase = EglBase.create()

        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
            .createPeerConnectionFactory()

        Log.i(TAG, "PeerConnectionFactory created")
    }

    /**
     * Step 2 — create the RTCPeerConnection.
     */
    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        Log.i(TAG, "Creating PeerConnection with ${iceServers.size} ICE servers")
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, buildObserver())

        if (peerConnection == null) {
            Log.e(TAG, "Failed to create PeerConnection — factory returned null")
            listener?.onError("PeerConnection creation failed")
        } else {
            Log.i(TAG, "PeerConnection created successfully")
        }
    }

    /**
     * Step 3 — open camera + microphone and add their tracks to the peer connection.
     * Must be called BEFORE createOffer() so the SDP contains sendrecv directions.
     */
    fun startLocalCapture(context: Context) {
        val pc = peerConnection ?: run {
            Log.e(TAG, "startLocalCapture: peerConnection is null")
            return
        }
        val fac = factory ?: run {
            Log.e(TAG, "startLocalCapture: factory is null")
            return
        }
        val egl = eglBase ?: run {
            Log.e(TAG, "startLocalCapture: eglBase is null")
            return
        }

        // ---- Audio track -------------------------------------------------------
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        }
        val audioSource = fac.createAudioSource(audioConstraints)
        localAudioTrack = fac.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack?.setEnabled(true)
        pc.addTrack(localAudioTrack, listOf(STREAM_ID))
        Log.i(TAG, "Audio track added to PeerConnection")

        // ---- Video track -------------------------------------------------------
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        Log.d(TAG, "Cameras: ${deviceNames.toList()}")

        val capturer = deviceNames
            .firstOrNull { enumerator.isFrontFacing(it) }
            ?.let { enumerator.createCapturer(it, null) }
            ?: deviceNames.firstOrNull()?.let { enumerator.createCapturer(it, null) }

        if (capturer == null) {
            Log.e(TAG, "No camera found — continuing without video")
            return
        }
        videoCapturer = capturer

        surfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", egl.eglBaseContext)
        localVideoSource = fac.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
        capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)

        localVideoTrack = fac.createVideoTrack(VIDEO_TRACK_ID, localVideoSource)
        localVideoTrack?.setEnabled(true)
        pc.addTrack(localVideoTrack, listOf(STREAM_ID))
        Log.i(TAG, "Video track added, capture started at ${VIDEO_WIDTH}x${VIDEO_HEIGHT}@${VIDEO_FPS}fps")

        // If renderer was attached before capture started, wire it now
        localRenderer?.let { localVideoTrack?.addSink(it) }
    }

    /**
     * Step 4 — create and send an SDP offer.
     */
    fun createOffer() {
        Log.i(TAG, "Creating SDP offer")
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let { desc ->
                    Log.d(TAG, "SDP offer created, setting as local description")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "Local description set — offer ready to send")
                            listener?.onLocalSdpReady("offer", desc.description)
                        }
                        override fun onCreateFailure(error: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "setLocalDescription failed: $error")
                            listener?.onError("setLocalDescription failed: $error")
                        }
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failed: $error")
                listener?.onError("createOffer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    /**
     * Handle an SDP answer from the operator.
     */
    fun handleAnswer(sdp: String) {
        Log.i(TAG, "Handling remote SDP answer (length=${sdp.length})")
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote description (answer) set successfully")
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription (answer) failed: $error")
                listener?.onError("setRemoteDescription (answer) failed: $error")
            }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    /**
     * Add a remote ICE candidate received via signaling.
     */
    fun addIceCandidate(ice: JSONObject) {
        val candidate = IceCandidate(
            ice.optString("sdpMid"),
            ice.optInt("sdpMLineIndex"),
            ice.optString("candidate")
        )
        Log.d(TAG, "Adding remote ICE candidate: mid=${candidate.sdpMid}")
        peerConnection?.addIceCandidate(candidate)
    }

    // ---------------------------------------------------------------------------
    // Renderer wiring (called from CallFragment after views are ready)
    // ---------------------------------------------------------------------------

    /**
     * Attach a SurfaceViewRenderer for displaying the local camera preview.
     * Must be initialised with getEglBaseContext() before calling this.
     */
    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        Log.d(TAG, "Attaching local renderer")
        localRenderer = renderer
        localVideoTrack?.addSink(renderer)
    }

    /**
     * Attach a SurfaceViewRenderer for displaying the remote (operator) video.
     * Must be initialised with getEglBaseContext() before calling this.
     */
    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        Log.d(TAG, "Attaching remote renderer")
        remoteRenderer = renderer
        // If the remote track already arrived, wire it immediately
        remoteVideoTrack?.addSink(renderer)
    }

    // ---------------------------------------------------------------------------
    // Release
    // ---------------------------------------------------------------------------

    /** Toggle microphone mute (enable/disable the audio track). */
    fun toggleMute() {
        localAudioTrack?.let { track ->
            val muted = track.enabled()   // currently enabled → we're about to mute
            track.setEnabled(!muted)
            Log.i(TAG, "Microphone ${if (muted) "muted" else "unmuted"}")
        }
    }

    /** Toggle camera on/off (enable/disable the video track). */
    fun toggleCamera() {
        localVideoTrack?.let { track ->
            val active = track.enabled()
            track.setEnabled(!active)
            Log.i(TAG, "Camera ${if (active) "disabled" else "enabled"}")
        }
    }

    fun close() {
        Log.i(TAG, "Closing WebRTC resources")
        mainHandler.removeCallbacks(iceDisconnectRunnable)

        // Detach renderers from tracks BEFORE releasing them to avoid
        // "EglRenderer: Dropping frame - Not initialized or already released"
        // that fires when the camera delivers frames after the renderer is gone.
        localRenderer?.let  { localVideoTrack?.removeSink(it);  localRenderer  = null }
        remoteRenderer?.let { remoteVideoTrack?.removeSink(it); remoteRenderer = null }

        try { videoCapturer?.stopCapture() } catch (e: InterruptedException) {
            Log.w(TAG, "stopCapture interrupted: ${e.message}")
        }
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        localVideoSource?.dispose()
        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        peerConnection?.close()
        factory?.dispose()
        eglBase?.release()

        videoCapturer = null
        surfaceTextureHelper = null
        localVideoSource = null
        localVideoTrack = null
        localAudioTrack = null
        remoteVideoTrack = null
        peerConnection = null
        factory = null
        eglBase = null
        Log.i(TAG, "WebRTC resources released")
    }

    // ---------------------------------------------------------------------------
    // PeerConnectionObserver
    // ---------------------------------------------------------------------------

    private fun buildObserver() = object : PeerConnection.Observer {

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(TAG, "Signaling state: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.i(TAG, "ICE connection state: $state")
            when (state) {
                PeerConnection.IceConnectionState.DISCONNECTED -> {
                    // Transient — WebRTC tries to recover automatically.
                    // Start a timeout: if not recovered in 10s, end the call.
                    Log.w(TAG, "ICE DISCONNECTED — waiting for recovery or FAILED (timeout ${iceDisconnectTimeoutMs}ms)")
                    mainHandler.postDelayed(iceDisconnectRunnable, iceDisconnectTimeoutMs)
                }
                PeerConnection.IceConnectionState.FAILED -> {
                    mainHandler.removeCallbacks(iceDisconnectRunnable)
                    Log.e(TAG, "ICE FAILED — ending call")
                    listener?.onCallEnded()
                }
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> {
                    // Cancel any pending disconnect timeout on recovery
                    mainHandler.removeCallbacks(iceDisconnectRunnable)
                    Log.i(TAG, "ICE $state — peer-to-peer link established")
                }
                else -> {}
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "ICE receiving: $receiving")
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "ICE gathering: $state")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "ICE candidate generated: mid=${it.sdpMid} idx=${it.sdpMLineIndex}")
                listener?.onIceCandidateGenerated(it)
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "ICE candidates removed: ${candidates?.size ?: 0}")
        }

        override fun onAddStream(stream: org.webrtc.MediaStream?) {
            // Deprecated in Unified Plan — use onAddTrack instead
            Log.d(TAG, "onAddStream (legacy): videoTracks=${stream?.videoTracks?.size}")
        }

        override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}

        override fun onDataChannel(channel: org.webrtc.DataChannel?) {
            Log.d(TAG, "DataChannel: ${channel?.label()}")
        }

        override fun onRenegotiationNeeded() {
            Log.i(TAG, "Renegotiation needed")
        }

        override fun onAddTrack(
            receiver: RtpReceiver?,
            streams: Array<out org.webrtc.MediaStream>?
        ) {
            val track = receiver?.track()
            Log.i(TAG, "Remote track received: kind=${track?.kind()}")
            if (track is VideoTrack) {
                remoteVideoTrack = track
                remoteRenderer?.let { track.addSink(it) }
                Log.i(TAG, "Remote VideoTrack attached to renderer=${remoteRenderer != null}")
            }
        }
    }
}
