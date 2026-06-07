package com.videokiosk.operator;

import com.videokiosk.operator.service.SignalingService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Main JavaFX application entry point.
 */
public class MainApp extends Application {

    private SignalingService signalingService;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load application properties
        Properties props = loadProperties();
        String serverUrl = props.getProperty("server.url", "ws://localhost:8080");

        // TODO: initialize SignalingService and pass to controllers via a shared context
        signalingService = new SignalingService(serverUrl);

        // Load main FXML view
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/videokiosk/operator/main-view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Оператор — VideoKiosk");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Connect to signaling server after UI is shown
        // TODO: wire SignalingService listener to MainViewModel
        signalingService.connect();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if (signalingService != null) {
            signalingService.disconnect();
        }
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
            }
        } catch (IOException e) {
            System.err.println("[MainApp] Could not load application.properties: " + e.getMessage());
        }
        return props;
    }

    // JavaFX requires this static launcher when using modules
    public static void main(String[] args) {
        launch(args);
    }
}
