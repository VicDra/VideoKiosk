package com.videokiosk.operator.viewmodel;

import com.videokiosk.operator.model.KioskCall;
import com.videokiosk.operator.model.KioskCall.CallStatus;
import com.videokiosk.operator.service.SignalingService;
import com.videokiosk.operator.service.WebRTCService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ViewModel for the main operator screen.
 * Holds the list of incoming calls and the currently active call state.
 */
public class MainViewModel {

    private static final Logger log = LoggerFactory.getLogger(MainViewModel.class);

    private final ObservableList<KioskCall> incomingCalls =
            FXCollections.observableArrayList();

    private final ObjectProperty<KioskCall> activeCall =
            new SimpleObjectProperty<>(null);

    private final BooleanProperty inCall = new SimpleBooleanProperty(false);

    private SignalingService signalingService;
    private WebRTCService   webRTCService;

    // Callback fired when WebRTC state changes (for CallController to hook)
    private WebRTCService.WebRTCListener webRTCListener;

    public MainViewModel() {}

    // -------------------------------------------------------------------------
    // Dependency injection (called from MainController.initSignaling)
    // -------------------------------------------------------------------------

    public void setSignalingService(SignalingService service) {
        this.signalingService = service;
    }

    public void setWebRTCListener(WebRTCService.WebRTCListener listener) {
        this.webRTCListener = listener;
    }

    // -------------------------------------------------------------------------
    // Public actions
    // -------------------------------------------------------------------------

    public void acceptCall(KioskCall call) {
        if (call == null) return;
        log.info("Accepting call from clientId={}", call.getClientId());

        call.setStatus(CallStatus.ACCEPTED);
        activeCall.set(call);
        inCall.set(true);
        incomingCalls.remove(call);

        // Send accept via signaling
        if (signalingService != null) {
            JSONObject msg = new JSONObject();
            msg.put("type", "accept");
            msg.put("clientId", call.getClientId());
            signalingService.send(msg);
            log.info("Sent accept to server for clientId={}", call.getClientId());
        } else {
            log.warn("acceptCall — signalingService is null, cannot send");
        }

        // Create WebRTCService now (waits for offer from kiosk)
        webRTCService = new WebRTCService();
        if (webRTCListener != null) {
            webRTCService.setListener(webRTCListener);
        }
        log.info("WebRTCService created, waiting for SDP offer from kiosk");
    }

    public void rejectCall(KioskCall call) {
        if (call == null) return;
        log.info("Rejecting call from clientId={}", call.getClientId());
        call.setStatus(CallStatus.REJECTED);
        incomingCalls.remove(call);

        if (signalingService != null) {
            JSONObject msg = new JSONObject();
            msg.put("type", "reject");
            msg.put("clientId", call.getClientId());
            signalingService.send(msg);
        }
    }

    public void endCurrentCall() {
        KioskCall call = activeCall.get();
        if (call == null) return;
        log.info("Ending active call with clientId={}", call.getClientId());

        if (webRTCService != null) {
            webRTCService.stopCall();
            webRTCService = null;
        }

        if (signalingService != null) {
            JSONObject msg = new JSONObject();
            msg.put("type", "end_call");
            msg.put("clientId", call.getClientId());
            msg.put("target", "client");
            signalingService.send(msg);
        }

        activeCall.set(null);
        inCall.set(false);
        log.info("Call ended");
    }

    // -------------------------------------------------------------------------
    // Signaling message handler (called from MainController listener)
    // -------------------------------------------------------------------------

    public void onSignalingMessage(JSONObject message) {
        String type = message.optString("type");
        log.debug("Signaling message: type={}", type);

        switch (type) {
            case "incoming_call": {
                String clientId = message.optString("clientId");
                log.info("Incoming call from clientId={} — adding to list", clientId);
                KioskCall call = new KioskCall(clientId);
                Platform.runLater(() -> incomingCalls.add(call));
                break;
            }

            case "offer": {
                String sdp      = message.optString("sdp");
                String clientId = message.optString("clientId");
                log.info("SDP offer from clientId={} (length={})", clientId, sdp.length());

                if (webRTCService == null) {
                    log.warn("Received offer but WebRTCService not ready — creating now");
                    webRTCService = new WebRTCService();
                    if (webRTCListener != null) webRTCService.setListener(webRTCListener);
                }
                // handleOffer runs blocking native code — use background thread
                final WebRTCService svc = webRTCService;
                final SignalingService sig = signalingService;
                Thread t = new Thread(() -> svc.handleOffer(sdp, clientId, sig), "webrtc-offer");
                t.setDaemon(true);
                t.start();
                break;
            }

            case "answer": {
                // Operator is always the answerer in our flow; this shouldn't arrive
                log.warn("Unexpected 'answer' message received by operator — ignored");
                break;
            }

            case "ice_candidate": {
                JSONObject ice = message.optJSONObject("ice");
                if (ice != null && webRTCService != null) {
                    log.debug("Remote ICE candidate from kiosk");
                    webRTCService.addIceCandidate(ice);
                } else if (ice == null) {
                    log.warn("ice_candidate message missing 'ice' field");
                } else {
                    log.warn("ICE candidate received but WebRTCService not ready — dropped");
                }
                break;
            }

            case "end_call": {
                log.info("Kiosk ended call");
                Platform.runLater(this::endCurrentCall);
                break;
            }

            default:
                log.warn("Unhandled message type: {}", type);
        }
    }

    // -------------------------------------------------------------------------
    // Property accessors
    // -------------------------------------------------------------------------

    public ObservableList<KioskCall> getIncomingCalls() { return incomingCalls; }
    public ObjectProperty<KioskCall> activeCallProperty() { return activeCall; }
    public KioskCall getActiveCall() { return activeCall.get(); }
    public BooleanProperty inCallProperty() { return inCall; }
    public boolean isInCall() { return inCall.get(); }
    public WebRTCService getWebRTCService() { return webRTCService; }
}
