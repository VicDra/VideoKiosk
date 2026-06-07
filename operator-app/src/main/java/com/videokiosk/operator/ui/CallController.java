package com.videokiosk.operator.ui;

import com.videokiosk.operator.service.WebRTCService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;

/**
 * JavaFX controller for call-view.fxml.
 * Displays local and remote video streams during an active call.
 */
public class CallController {

    // ---------------------------------------------------------------------------
    // FXML bindings
    // ---------------------------------------------------------------------------

    /**
     * Full-screen view for the remote video feed.
     * TODO: Replace with a Canvas or ImageView if using a custom WebRTC renderer.
     */
    @FXML
    private MediaView remoteVideoView;

    /**
     * Picture-in-picture view for the local camera feed.
     * TODO: Replace with a Canvas or ImageView if using a custom WebRTC renderer.
     */
    @FXML
    private MediaView localVideoView;

    @FXML
    private Button endCallButton;

    @FXML
    private Label callStatusLabel;

    @FXML
    private StackPane remoteVideoPane;

    @FXML
    private StackPane localVideoPane;

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private WebRTCService webRTCService;
    private String remoteClientId;

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    @FXML
    public void initialize() {
        callStatusLabel.setText("Подключение...");

        // TODO: attach video renderers once WebRTCService is injected
        // TODO: update callStatusLabel when WebRTC connection is established
    }

    /**
     * Inject dependencies after FXML is loaded.
     * @param webRTCService  The WebRTC service managing the peer connection.
     * @param remoteClientId The kiosk client ID for display purposes.
     */
    public void setup(WebRTCService webRTCService, String remoteClientId) {
        this.webRTCService = webRTCService;
        this.remoteClientId = remoteClientId;

        callStatusLabel.setText("Звонок с: " + remoteClientId);

        // TODO: webRTCService.attachLocalRenderer(localVideoPane)
        // TODO: webRTCService.attachRemoteRenderer(remoteVideoPane)
    }

    // ---------------------------------------------------------------------------
    // Button handlers
    // ---------------------------------------------------------------------------

    @FXML
    public void handleEndCall() {
        if (webRTCService != null) {
            webRTCService.stopCall();
        }

        // TODO: notify ViewModel / SignalingService that call has ended
        System.out.println("[CallController] Call ended by operator");

        // Close call window
        Stage stage = (Stage) endCallButton.getScene().getWindow();
        stage.close();
    }
}
