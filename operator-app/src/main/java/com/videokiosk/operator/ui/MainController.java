package com.videokiosk.operator.ui;

import com.videokiosk.operator.model.KioskCall;
import com.videokiosk.operator.service.SignalingService;
import com.videokiosk.operator.service.WebRTCService;
import com.videokiosk.operator.viewmodel.MainViewModel;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JavaFX controller for main-view.fxml.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    // -------------------------------------------------------------------------
    // FXML bindings
    // -------------------------------------------------------------------------

    @FXML private ListView<KioskCall> callsListView;
    @FXML private Button acceptButton;
    @FXML private Button rejectButton;
    @FXML private Label  statusLabel;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final MainViewModel viewModel = new MainViewModel();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @FXML
    public void initialize() {
        log.info("MainController initializing");

        callsListView.setItems(viewModel.getIncomingCalls());

        acceptButton.disableProperty().bind(
                callsListView.getSelectionModel().selectedItemProperty().isNull()
                        .or(viewModel.inCallProperty()));
        rejectButton.disableProperty().bind(
                callsListView.getSelectionModel().selectedItemProperty().isNull());

        viewModel.inCallProperty().addListener((obs, wasInCall, nowInCall) -> {
            if (nowInCall) {
                String cid = viewModel.getActiveCall().getClientId();
                statusLabel.setText("В разговоре с: " + cid);
                log.info("Call active with clientId={}", cid);
            } else {
                statusLabel.setText("Ожидание вызовов...");
                log.info("Call ended — waiting for next call");
            }
        });

        statusLabel.setText("Ожидание вызовов...");
        log.info("MainController ready");
    }

    // -------------------------------------------------------------------------
    // Wiring (called from MainApp after FXML load)
    // -------------------------------------------------------------------------

    public void initSignaling(SignalingService service) {
        viewModel.setSignalingService(service);

        // Pass a WebRTCListener so the call window updates with video frames
        viewModel.setWebRTCListener(new WebRTCService.WebRTCListener() {
            @Override
            public void onRemoteVideoFrame(WritableImage image) {
                // Frame is delivered directly to CallController via its own listener —
                // this top-level listener is a fallback / unused at runtime
            }
            @Override
            public void onCallConnected() {
                log.info("WebRTC peer connection established");
                Platform.runLater(() -> statusLabel.setText("Видеозвонок активен"));
            }
            @Override
            public void onCallEnded() {
                log.info("WebRTC call ended — returning to idle");
                Platform.runLater(() -> {
                    viewModel.endCurrentCall();
                    statusLabel.setText("Ожидание вызовов...");
                });
            }
            @Override
            public void onError(String message) {
                log.error("WebRTC error: {}", message);
                Platform.runLater(() -> statusLabel.setText("Ошибка WebRTC: " + message));
            }
        });

        service.setListener(new SignalingService.SignalingListener() {
            @Override
            public void onMessage(org.json.JSONObject message) {
                log.debug("Signaling message: type={}", message.optString("type"));
                viewModel.onSignalingMessage(message);
            }
            @Override
            public void onConnected() {
                log.info("Signaling connected");
                Platform.runLater(() -> statusLabel.setText("Подключено. Ожидание вызовов..."));
            }
            @Override
            public void onDisconnected(int code, String reason) {
                log.warn("Signaling disconnected: code={} reason={}", code, reason);
                Platform.runLater(() -> statusLabel.setText("Нет соединения (код " + code + ")"));
            }
            @Override
            public void onError(Exception ex) {
                log.error("Signaling error: {}", ex.getMessage(), ex);
                Platform.runLater(() -> statusLabel.setText("Ошибка: " + ex.getMessage()));
            }
        });

        log.info("SignalingService and WebRTCListener wired to MainController");
    }

    // -------------------------------------------------------------------------
    // Button handlers
    // -------------------------------------------------------------------------

    @FXML
    public void handleAccept() {
        KioskCall selected = callsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            log.warn("handleAccept: no selection");
            return;
        }
        log.info("Accepting call from clientId={}", selected.getClientId());
        viewModel.acceptCall(selected);
        openCallWindow(selected.getClientId());
    }

    @FXML
    public void handleReject() {
        KioskCall selected = callsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            log.warn("handleReject: no selection");
            return;
        }
        log.info("Rejecting call from clientId={}", selected.getClientId());
        viewModel.rejectCall(selected);
    }

    // -------------------------------------------------------------------------
    // Call window
    // -------------------------------------------------------------------------

    private void openCallWindow(String clientId) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/videokiosk/operator/call-view.fxml"));
            Scene scene = new Scene(loader.load());

            CallController callCtrl = loader.getController();
            WebRTCService svc = viewModel.getWebRTCService();
            if (svc != null) {
                callCtrl.setup(svc, clientId);
            } else {
                log.warn("openCallWindow: WebRTCService not yet created — call window opened without WebRTC");
            }
            // Wire end-call callback: operator button → send end_call to kiosk via signaling
            callCtrl.setOnCallEnded(() -> viewModel.endCurrentCall());

            Stage callStage = new Stage();
            callStage.setTitle("Звонок: " + clientId);
            callStage.setScene(scene);
            callStage.setOnCloseRequest(e -> {
                log.info("Call window closed by user");
                viewModel.endCurrentCall();
            });
            callStage.show();
            log.info("Call window opened for clientId={}", clientId);

        } catch (IOException e) {
            log.error("Failed to open call window: {}", e.getMessage(), e);
        }
    }
}
