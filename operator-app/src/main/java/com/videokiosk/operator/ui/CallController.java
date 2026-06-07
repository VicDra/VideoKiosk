package com.videokiosk.operator.ui;

import com.videokiosk.operator.service.WebRTCService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX controller for call-view.fxml.
 * Displays the remote video feed from the kiosk via WebRTC.
 */
public class CallController {

    private static final Logger log = LoggerFactory.getLogger(CallController.class);

    // -------------------------------------------------------------------------
    // FXML bindings
    // -------------------------------------------------------------------------

    @FXML private ImageView  remoteVideoView;
    @FXML private ImageView  localVideoView;
    @FXML private Button     endCallButton;
    @FXML private Label      callStatusLabel;
    @FXML private StackPane  remoteVideoPane;
    @FXML private StackPane  localVideoPane;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private WebRTCService webRTCService;
    private String        remoteClientId;

    // Callback invoked when the operator explicitly ends the call
    // (wired by MainController so the ViewModel can send end_call to kiosk)
    private Runnable onCallEnded;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @FXML
    public void initialize() {
        log.info("CallController initializing");
        callStatusLabel.setText("Подключение...");
        remoteVideoView.setPreserveRatio(true);
        remoteVideoView.setFitWidth(800);
        remoteVideoView.setFitHeight(540);
    }

    /**
     * Inject WebRTCService and remote client ID after FXML is loaded.
     * Registers the video-frame callback so each decoded frame is shown in remoteVideoView.
     */
    /** Set the callback that fires when the operator ends the call (used to notify kiosk). */
    public void setOnCallEnded(Runnable callback) {
        this.onCallEnded = callback;
    }

    public void setup(WebRTCService service, String clientId) {
        this.webRTCService  = service;
        this.remoteClientId = clientId;
        log.info("CallController.setup: clientId={}", clientId);

        callStatusLabel.setText("Звонок с: " + clientId);

        service.setListener(new WebRTCService.WebRTCListener() {
            @Override
            public void onRemoteVideoFrame(WritableImage image) {
                // Already on FX thread (Platform.runLater in WebRTCService)
                remoteVideoView.setImage(image);
            }

            @Override
            public void onCallConnected() {
                log.info("WebRTC connected — call live with clientId={}", clientId);
                callStatusLabel.setText("Звонок активен: " + clientId);
            }

            @Override
            public void onCallEnded() {
                log.info("WebRTC call ended");
                callStatusLabel.setText("Звонок завершён");
                closeWindow();
            }

            @Override
            public void onError(String message) {
                log.error("WebRTC error in call with {}: {}", clientId, message);
                callStatusLabel.setText("Ошибка: " + message);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Button handlers
    // -------------------------------------------------------------------------

    @FXML
    public void handleEndCall() {
        log.info("Operator ended call with clientId={}", remoteClientId);
        if (webRTCService != null) {
            webRTCService.stopCall();
        }
        // Notify ViewModel so it sends end_call to the kiosk via signaling
        if (onCallEnded != null) {
            onCallEnded.run();
        }
        closeWindow();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void closeWindow() {
        if (endCallButton.getScene() != null) {
            Stage stage = (Stage) endCallButton.getScene().getWindow();
            stage.close();
        }
    }
}
