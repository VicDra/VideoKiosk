package com.videokiosk.operator.service;

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceConnectionState;
import dev.onvoid.webrtc.RTCIceGatheringState;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionIceErrorEvent;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCRtpReceiver;
import dev.onvoid.webrtc.RTCRtpTransceiver;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCSignalingState;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import dev.onvoid.webrtc.media.MediaStream;
import dev.onvoid.webrtc.media.MediaStreamTrack;
import dev.onvoid.webrtc.media.video.I420Buffer;
import dev.onvoid.webrtc.media.video.VideoFrame;
import dev.onvoid.webrtc.media.video.VideoTrack;
import javafx.application.Platform;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * WebRTC peer-connection service for the operator desktop application.
 * Uses dev.onvoid.webrtc (JNI wrapper for native WebRTC) to receive video/audio from the kiosk.
 *
 * Role: ANSWERER — waits for an SDP offer from the kiosk, creates an SDP answer,
 * exchanges ICE candidates, and pipes incoming video frames to a JavaFX ImageView.
 */
public class WebRTCService {

    private static final Logger log = LoggerFactory.getLogger(WebRTCService.class);

    // -------------------------------------------------------------------------
    // Listener (UI callbacks, always invoked on the FX thread)
    // -------------------------------------------------------------------------

    public interface WebRTCListener {
        void onRemoteVideoFrame(WritableImage image);
        void onCallConnected();
        void onCallEnded();
        void onError(String message);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PeerConnectionFactory factory;
    private RTCPeerConnection peerConnection;
    private WebRTCListener listener;

    // ICE candidates that arrived before peerConnection was created — applied after handleOffer
    private final List<JSONObject> pendingIceCandidates = new ArrayList<>();

    // Reused WritableImage — recreated if resolution changes
    private WritableImage remoteImage;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public WebRTCService() {
        log.info("Initialising PeerConnectionFactory (loading native WebRTC)");
        this.factory = new PeerConnectionFactory();
        log.info("PeerConnectionFactory ready");
    }

    public void setListener(WebRTCListener listener) {
        this.listener = listener;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Process an SDP offer from the kiosk:
     * creates PeerConnection → sets remote description → creates answer → sends it back.
     *
     * @param sdp      SDP offer string received from the kiosk via signaling
     * @param clientId ID of the kiosk that sent the offer
     * @param signaling SignalingService for sending the answer and ICE candidates
     */
    public void handleOffer(String sdp, String clientId, SignalingService signaling) {
        log.info("handleOffer: clientId={} sdpLen={}", clientId, sdp.length());

        RTCConfiguration config = buildConfig();
        peerConnection = factory.createPeerConnection(config, buildObserver(clientId, signaling));
        log.info("PeerConnection created");

        // Apply any ICE candidates that arrived before the peer connection was ready
        flushPendingIceCandidates();

        RTCSessionDescription offer = new RTCSessionDescription(RTCSdpType.OFFER, sdp);
        peerConnection.setRemoteDescription(offer, new SetSessionDescriptionObserver() {
            @Override
            public void onSuccess() {
                log.info("Remote description (offer) set — creating answer");
                createAndSendAnswer(clientId, signaling);
            }
            @Override
            public void onFailure(String error) {
                log.error("setRemoteDescription(offer) failed: {}", error);
                fireError("setRemoteDescription failed: " + error);
            }
        });
    }

    /**
     * Add an ICE candidate received from the kiosk.
     * If the peer connection is not yet ready (race: candidate arrived before handleOffer
     * finished creating the RTCPeerConnection), the candidate is buffered and applied
     * as soon as handleOffer completes.
     */
    public void addIceCandidate(JSONObject ice) {
        if (peerConnection == null) {
            log.debug("addIceCandidate: peerConnection not ready — buffering candidate");
            pendingIceCandidates.add(ice);
            return;
        }
        applyIceCandidate(ice);
    }

    private void applyIceCandidate(JSONObject ice) {
        // JSON key "candidate" maps to the RTCIceCandidate.sdp field
        RTCIceCandidate candidate = new RTCIceCandidate(
                ice.optString("sdpMid"),
                ice.optInt("sdpMLineIndex"),
                ice.optString("candidate")   // ← the actual SDP candidate line
        );
        log.debug("Adding remote ICE candidate: mid={} index={}", candidate.sdpMid, candidate.sdpMLineIndex);
        peerConnection.addIceCandidate(candidate);
    }

    private void flushPendingIceCandidates() {
        if (!pendingIceCandidates.isEmpty()) {
            log.info("Flushing {} buffered ICE candidates", pendingIceCandidates.size());
            for (JSONObject ice : pendingIceCandidates) {
                applyIceCandidate(ice);
            }
            pendingIceCandidates.clear();
        }
    }

    /** Close the peer connection and release WebRTC resources. Idempotent. */
    public void stopCall() {
        log.info("Stopping call");
        pendingIceCandidates.clear();
        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
        // Note: factory is NOT disposed here because it is shared across calls
        // (re-created per-call via MainViewModel). Safe to leave for GC.
        log.info("WebRTC resources disposed");
    }

    // -------------------------------------------------------------------------
    // Private — answer creation
    // -------------------------------------------------------------------------

    private void createAndSendAnswer(String clientId, SignalingService signaling) {
        peerConnection.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
            @Override
            public void onSuccess(RTCSessionDescription answer) {
                log.info("SDP answer created — setting as local description");
                peerConnection.setLocalDescription(answer, new SetSessionDescriptionObserver() {
                    @Override
                    public void onSuccess() {
                        log.info("Local description set — sending answer to clientId={}", clientId);
                        JSONObject msg = new JSONObject();
                        msg.put("type", "answer");
                        msg.put("sdp", answer.sdp);
                        msg.put("target", "client");
                        msg.put("clientId", clientId);
                        signaling.send(msg);
                    }
                    @Override
                    public void onFailure(String error) {
                        log.error("setLocalDescription(answer) failed: {}", error);
                        fireError("setLocalDescription failed: " + error);
                    }
                });
            }
            @Override
            public void onFailure(String error) {
                log.error("createAnswer failed: {}", error);
                fireError("createAnswer failed: " + error);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Private — PeerConnectionObserver
    // -------------------------------------------------------------------------

    private PeerConnectionObserver buildObserver(String clientId, SignalingService signaling) {
        return new PeerConnectionObserver() {

            // Only abstract method — must implement
            @Override
            public void onIceCandidate(RTCIceCandidate candidate) {
                log.debug("Local ICE candidate → client {}: mid={}", clientId, candidate.sdpMid);
                JSONObject ice = new JSONObject();
                ice.put("sdpMid",        candidate.sdpMid);
                ice.put("sdpMLineIndex", candidate.sdpMLineIndex);
                ice.put("candidate",     candidate.sdp);   // field name "candidate" in protocol

                JSONObject msg = new JSONObject();
                msg.put("type",     "ice_candidate");
                msg.put("target",   "client");
                msg.put("clientId", clientId);
                msg.put("ice",      ice);
                signaling.send(msg);
            }

            // Override selected default methods for logging/handling

            @Override
            public void onConnectionChange(RTCPeerConnectionState state) {
                log.info("PeerConnection state: {}", state);
                switch (state) {
                    case CONNECTED:
                        log.info("WebRTC CONNECTED to kiosk={}", clientId);
                        Platform.runLater(() -> { if (listener != null) listener.onCallConnected(); });
                        break;
                    case FAILED:
                        log.error("WebRTC FAILED for kiosk={}", clientId);
                        fireError("Connection failed");
                        break;
                    case DISCONNECTED:
                    case CLOSED:
                        log.warn("WebRTC {} for kiosk={}", state, clientId);
                        Platform.runLater(() -> { if (listener != null) listener.onCallEnded(); });
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onSignalingChange(RTCSignalingState state) {
                log.debug("Signaling state: {}", state);
            }

            @Override
            public void onIceGatheringChange(RTCIceGatheringState state) {
                log.debug("ICE gathering: {}", state);
            }

            @Override
            public void onIceConnectionChange(RTCIceConnectionState state) {
                log.info("ICE connection: {}", state);
            }

            @Override
            public void onIceCandidateError(RTCPeerConnectionIceErrorEvent event) {
                log.warn("ICE error: {} ({})", event.getErrorText(), event.getErrorCode());
            }

            @Override
            public void onTrack(RTCRtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().getTrack();
                log.info("Remote track: kind={}", track.getKind());
                if (track instanceof VideoTrack) {
                    VideoTrack videoTrack = (VideoTrack) track;
                    log.info("Attaching video sink to remote VideoTrack");
                    videoTrack.addSink(frame -> renderVideoFrame(frame));
                }
            }

            @Override
            public void onRenegotiationNeeded() {
                log.info("Renegotiation needed");
            }
        };
    }

    // -------------------------------------------------------------------------
    // Private — ICE config
    // -------------------------------------------------------------------------

    private RTCConfiguration buildConfig() {
        RTCConfiguration config = new RTCConfiguration();

        // STUN — gathers reflexive candidates; also works as a fallback on open networks
        RTCIceServer stun = new RTCIceServer();
        stun.urls = List.of(
                "stun:stun.l.google.com:19302",
                "stun:stun1.l.google.com:19302"
        );

        // Local TCP TURN relay — local-turn-server.js running on this PC.
        // Kiosk connects via ADB reverse tunnel (tcp:3478→tcp:3478), operator connects
        // directly. Both reach the same relay on 127.0.0.1:3478.
        // WinHTTP always bypasses the system proxy for loopback, so Hiddify won't
        // interfere even when the proxy is enabled.
        RTCIceServer turn = new RTCIceServer();
        turn.urls = List.of("turn:127.0.0.1:3478?transport=tcp");
        turn.username = "user";
        turn.password = "password";

        config.iceServers = List.of(stun, turn);
        return config;
    }

    // -------------------------------------------------------------------------
    // Private — video frame rendering
    // -------------------------------------------------------------------------

    private void renderVideoFrame(VideoFrame frame) {
        try {
            I420Buffer i420 = frame.buffer.toI420();
            int width  = i420.getWidth();
            int height = i420.getHeight();

            byte[] bgra = i420ToBgra(i420, width, height);
            i420.release();

            Platform.runLater(() -> {
                if (remoteImage == null
                        || (int) remoteImage.getWidth()  != width
                        || (int) remoteImage.getHeight() != height) {
                    remoteImage = new WritableImage(width, height);
                    log.debug("Remote WritableImage created: {}x{}", width, height);
                }
                remoteImage.getPixelWriter().setPixels(
                        0, 0, width, height,
                        PixelFormat.getByteBgraPreInstance(),
                        bgra, 0, width * 4);
                if (listener != null) listener.onRemoteVideoFrame(remoteImage);
            });
        } catch (Exception e) {
            log.error("renderVideoFrame error: {}", e.getMessage(), e);
        }
    }

    /**
     * Convert I420 (YUV planar) to BGRA (JavaFX getByteBgraPreInstance).
     * Integer arithmetic for speed (~40 fps on i7 at 720p).
     */
    private static byte[] i420ToBgra(I420Buffer i420, int width, int height) {
        ByteBuffer yBuf = i420.getDataY();
        ByteBuffer uBuf = i420.getDataU();
        ByteBuffer vBuf = i420.getDataV();
        int strideY = i420.getStrideY();
        int strideU = i420.getStrideU();
        int strideV = i420.getStrideV();

        byte[] out = new byte[width * height * 4];
        int idx = 0;

        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                int y = (yBuf.get(row * strideY + col) & 0xFF);
                int u = (uBuf.get((row >> 1) * strideU + (col >> 1)) & 0xFF) - 128;
                int v = (vBuf.get((row >> 1) * strideV + (col >> 1)) & 0xFF) - 128;

                int r = clamp(y + (int)(1.402f  * v));
                int g = clamp(y - (int)(0.344f  * u) - (int)(0.714f * v));
                int b = clamp(y + (int)(1.772f  * u));

                out[idx++] = (byte) b;   // B
                out[idx++] = (byte) g;   // G
                out[idx++] = (byte) r;   // R
                out[idx++] = (byte) 0xFF;// A
            }
        }
        return out;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private void fireError(String msg) {
        Platform.runLater(() -> { if (listener != null) listener.onError(msg); });
    }
}
