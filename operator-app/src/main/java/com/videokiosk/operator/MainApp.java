package com.videokiosk.operator;

import com.videokiosk.operator.service.SignalingService;
import com.videokiosk.operator.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Main JavaFX application entry point.
 */
public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    private SignalingService signalingService;

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("=== VideoKiosk Operator App starting ===");

        // Load application properties
        Properties props = loadProperties();
        String serverUrl = props.getProperty("server.url", "ws://localhost:8080");
        log.info("Signaling server URL: {}", serverUrl);

        signalingService = new SignalingService(serverUrl);

        // Load main FXML — controller is created here
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/videokiosk/operator/main-view.fxml"));
        Parent root = loader.load();

        // Wire SignalingService into the controller BEFORE connecting
        MainController controller = loader.getController();
        controller.initSignaling(signalingService);
        log.info("SignalingService wired to MainController");

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Оператор — VideoKiosk");
        primaryStage.setScene(scene);
        primaryStage.show();
        log.info("Main window displayed");

        // Connect on a background thread so the FX thread is never blocked
        Thread connectThread = new Thread(() -> {
            log.info("Connecting to signaling server (background thread)...");
            signalingService.connect();
        }, "signaling-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        log.info("Application stopping — disconnecting from signaling server");
        if (signalingService != null) {
            signalingService.disconnect();
        }
        log.info("=== VideoKiosk Operator App stopped ===");
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream(
                "/com/videokiosk/operator/application.properties")) {
            if (in != null) {
                props.load(in);
                log.debug("application.properties loaded: {}", props);
            } else {
                log.warn("application.properties not found on classpath — using defaults");
            }
        } catch (IOException e) {
            log.error("Could not load application.properties: {}", e.getMessage(), e);
        }
        return props;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
