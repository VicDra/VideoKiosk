package com.videokiosk.kiosk.service

import android.content.Context
import org.json.JSONObject
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * WebRTC client stub for the kiosk Android application.
 * Handles peer connection lifecycle, SDP negotiation, and ICE candidate exchange.
 *
 * TODO: Wire up SignalingClient callbacks so that ICE candidates and SDP
 *       are sent to the signaling server automatically.
 */
class WebRTCClient {

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

    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var listener: WebRTCListener? = null

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    fun setListener(listener: WebRTCListener) {
        this.listener = listener
    }

    /**
     * Initialize the WebRTC stack.
     * Must be called once before any other methods.
     *
     * @param context    Android context for hardware codec initialization.
     * @param iceServers List of STUN/TURN server configurations.
     */
    fun initialize(context: Context, iceServers: List<PeerConnection.IceServer>) {
        eglBase = EglBase.create()

        // Initialize PeerConnectionFactory
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase!!.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        // TODO: call createPeerConnection(iceServers) after initialization
    }

    /**
     * Create the PeerConnection with the given ICE server list.
     *
     * @param iceServers STUN/TURN server configurations.
     */
    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            // TODO: configure additional ICE transport policies if needed
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                println("[WebRTCClient] Signaling state: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                println("[WebRTCClient] ICE connection state: $state")
                if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                    state == PeerConnection.IceConnectionState.FAILED) {
                    listener?.onCallEnded()
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    // TODO: send ICE candidate via SignalingClient
                    listener?.onIceCandidateGenerated(it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: org.webrtc.MediaStream?) {
                // TODO: attach first video track to remoteRenderer
            }

            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}

            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}

            override fun onRenegotiationNeeded() {
                // TODO: re-negotiate if needed
            }

            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?
            ) {
                // TODO: handle incoming remote video/audio tracks
            }
        })
    }

    /**
     * Create an SDP offer to initiate a call.
     * The resulting SDP will be reported via [WebRTCListener.onLocalSdpReady].
     */
    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            listener?.onLocalSdpReady("offer", it.description)
                        }
                        override fun onCreateFailure(error: String?) {}
                        override fun onSetFailure(error: String?) {
                            listener?.onError("setLocalDescription failed: $error")
                        }
                    }, it)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                listener?.onError("createOffer failed: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    /**
     * Handle an incoming SDP offer (if this device is the answerer).
     * @param sdp SDP string from the remote peer.
     */
    fun handleOffer(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                // TODO: createAnswer() and send via signalingClient
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                listener?.onError("setRemoteDescription (offer) failed: $error")
            }
        }, sessionDescription)
    }

    /**
     * Handle an incoming SDP answer from the remote peer.
     * @param sdp SDP answer string received via signaling.
     */
    fun handleAnswer(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                println("[WebRTCClient] Remote description set successfully")
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                listener?.onError("setRemoteDescription (answer) failed: $error")
            }
        }, sessionDescription)
    }

    /**
     * Add an ICE candidate received via signaling.
     * @param ice JSONObject with fields: sdpMid, sdpMLineIndex, candidate.
     */
    fun addIceCandidate(ice: JSONObject) {
        val candidate = IceCandidate(
            ice.optString("sdpMid"),
            ice.optInt("sdpMLineIndex"),
            ice.optString("candidate")
        )
        peerConnection?.addIceCandidate(candidate)
    }

    /**
     * Attach the local camera feed to a [SurfaceViewRenderer].
     * The renderer must already be initialized with [SurfaceViewRenderer.init].
     */
    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderer = renderer
        localVideoTrack?.addSink(renderer)
        // TODO: start camera capture and add localVideoTrack to peer connection
    }

    /**
     * Attach the remote video feed to a [SurfaceViewRenderer].
     */
    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        // TODO: attach once remote video track is received via onAddTrack
    }

    /**
     * Start capturing from the front-facing (or only available) camera.
     * @param context Android context for camera access.
     */
    fun startLocalVideo(context: Context) {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Prefer front camera
        videoCapturer = deviceNames
            .firstOrNull { enumerator.isFrontFacing(it) }
            ?.let { enumerator.createCapturer(it, null) }
            ?: deviceNames.firstOrNull()?.let { enumerator.createCapturer(it, null) }

        videoCapturer?.let { capturer ->
            localVideoSource = factory?.createVideoSource(capturer.isScreencast)
            localVideoTrack = factory?.createVideoTrack("local_video_track", localVideoSource)
            // TODO: start the capturer with capturer.startCapture(width, height, fps)
            // TODO: add localVideoTrack to peer connection
        }
    }

    /**
     * Close the peer connection and release all WebRTC resources.
     */
    fun close() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoSource?.dispose()
        localVideoTrack?.dispose()
        peerConnection?.close()
        factory?.dispose()
        eglBase?.release()

        videoCapturer = null
        localVideoSource = null
        localVideoTrack = null
        peerConnection = null
        factory = null
        eglBase = null
    }
}
