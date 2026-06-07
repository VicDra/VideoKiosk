package com.videokiosk.operator.viewmodel;

import com.videokiosk.operator.model.KioskCall;
import com.videokiosk.operator.model.KioskCall.CallStatus;
import com.videokiosk.operator.service.SignalingService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.json.JSONObject;

/**
 * ViewModel for the main operator screen.
 * Holds the list of incoming calls and the currently active call state.
 */
public class MainViewModel {

    private final ObservableList<KioskCall> incomingCalls =
            FXCollections.observableArrayList();

    private final ObjectProperty<KioskCall> activeCall =
            new SimpleObjectProperty<>(null);

    private final BooleanProperty inCall = new SimpleBooleanProperty(false);

    private SignalingService signalingService;

    public MainViewModel() {
        // TODO: obtain SignalingService via dependency injection or application context
    }

    public void setSignalingService(SignalingService service) {
        this.signalingService = service;
    }

    // ---------------------------------------------------------------------------
    // Public actions
    // ---------------------------------------------------------------------------

    /**
     * Accept the given call and notify the signaling server.
     */
    public void acceptCall(KioskCall call) {
        if (call == null) return;

        call.setStatus(CallStatus.ACCEPTED);
        activeCall.set(call);
        inCall.set(true);
        incomingCalls.remove(call);

        // TODO: send accept message via SignalingService
        if (signalingService != null) {
            JSONObject msg = new JSONObject();
            msg.put("type", "accept");
            msg.put("clientId", call.getClientId());
            signalingService.send(msg);
        }
    }

    /**
     * Reject the given call and notify the signaling server.
     */
    public void rejectCall(KioskCall call) {
        if (call == null) return;

        call.setStatus(CallStatus.REJECTED);
        incomingCalls.remove(call);

        // TODO: send reject message via SignalingService
        if (signalingService != null) {
            JSONObject msg = new JSONObject();
            msg.put("type", "reject");
            msg.put("clientId", call.getClientId());
            signalingService.send(msg);
        }
    }

    /**
     * Handle a message received from the signaling server.
     * Must be called on any thread; updates are marshalled to the FX thread.
     */
    public void onSignalingMessage(JSONObject message) {
        String type = message.optString("type");

        switch (type) {
            case "incoming_call": {
                String clientId = message.optString("clientId");
                KioskCall call = new KioskCall(clientId);
                Platform.runLater(() -> incomingCalls.add(call));
                break;
            }
            case "offer": {
                // TODO: delegate to WebRTCService to handle the SDP offer
                break;
            }
            case "answer": {
                // TODO: delegate to WebRTCService to handle the SDP answer
                break;
            }
            case "ice_candidate": {
                // TODO: delegate to WebRTCService to add ICE candidate
                break;
            }
            default:
                System.out.println("[MainViewModel] Unhandled message type: " + type);
        }
    }

    // ---------------------------------------------------------------------------
    // Property accessors
    // ---------------------------------------------------------------------------

    public ObservableList<KioskCall> getIncomingCalls() {
        return incomingCalls;
    }

    public ObjectProperty<KioskCall> activeCallProperty() {
        return activeCall;
    }

    public KioskCall getActiveCall() {
        return activeCall.get();
    }

    public BooleanProperty inCallProperty() {
        return inCall;
    }

    public boolean isInCall() {
        return inCall.get();
    }
}
