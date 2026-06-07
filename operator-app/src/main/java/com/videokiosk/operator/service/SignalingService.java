package com.videokiosk.operator.service;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * WebSocket signaling client for the operator desktop application.
 * Connects to the Node.js signaling server and routes JSON messages
 * to a registered {@link SignalingListener}.
 */
public class SignalingService {

    private static final Logger log = LoggerFactory.getLogger(SignalingService.class);

    // ---------------------------------------------------------------------------
    // Listener interface
    // ---------------------------------------------------------------------------

    public interface SignalingListener {
        void onMessage(JSONObject message);
        void onConnected();
        void onDisconnected(int code, String reason);
        void onError(Exception ex);
    }

    // ---------------------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------------------

    private final String serverUrl;
    private WebSocketClient wsClient;
    private SignalingListener listener;

    public SignalingService(String serverUrl) {
        this.serverUrl = serverUrl;
        log.debug("SignalingService created, target={}", serverUrl);
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    public void setListener(SignalingListener listener) {
        this.listener = listener;
    }

    /**
     * Establish a WebSocket connection to the signaling server as operator.
     */
    public void connect() {
        String url = serverUrl + "?role=operator";
        log.info("Connecting to signaling server: {}", url);
        try {
            wsClient = new WebSocketClient(new URI(url)) {

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    log.info("Connected to signaling server (HTTP {})", handshakedata.getHttpStatus());
                    if (listener != null) listener.onConnected();
                }

                @Override
                public void onMessage(String raw) {
                    log.debug("Received: {}", raw);
                    try {
                        JSONObject msg = new JSONObject(raw);
                        if (listener != null) listener.onMessage(msg);
                    } catch (Exception e) {
                        log.error("Failed to parse message: {} — raw={}", e.getMessage(), raw);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("Disconnected: code={} reason={} remote={}", code, reason, remote);
                    if (listener != null) listener.onDisconnected(code, reason);
                }

                @Override
                public void onError(Exception ex) {
                    log.error("WebSocket error: {}", ex.getMessage(), ex);
                    if (listener != null) listener.onError(ex);
                }
            };

            wsClient.connectBlocking();
        } catch (URISyntaxException e) {
            log.error("Invalid server URL '{}': {}", serverUrl, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Connection attempt interrupted");
        }
    }

    /**
     * Send a JSON message to the signaling server.
     */
    public void send(JSONObject message) {
        if (wsClient != null && wsClient.isOpen()) {
            log.debug("Sending: {}", message);
            wsClient.send(message.toString());
        } else {
            log.warn("Cannot send — not connected. Message dropped: {}", message);
        }
    }

    /**
     * Close the WebSocket connection.
     */
    public void disconnect() {
        log.info("Disconnecting from signaling server");
        if (wsClient != null) {
            try {
                wsClient.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Disconnect interrupted");
            }
        }
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }
}
