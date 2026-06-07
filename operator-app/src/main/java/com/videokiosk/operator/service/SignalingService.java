package com.videokiosk.operator.service;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * WebSocket signaling client for the operator desktop application.
 * Connects to the Node.js signaling server and routes JSON messages
 * to a registered {@link SignalingListener}.
 */
public class SignalingService {

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
        try {
            wsClient = new WebSocketClient(new URI(url)) {

                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("[SignalingService] Connected to: " + url);
                    if (listener != null) {
                        listener.onConnected();
                    }
                }

                @Override
                public void onMessage(String raw) {
                    System.out.println("[SignalingService] Received: " + raw);
                    try {
                        JSONObject msg = new JSONObject(raw);
                        if (listener != null) {
                            listener.onMessage(msg);
                        }
                    } catch (Exception e) {
                        System.err.println("[SignalingService] Failed to parse message: " + e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("[SignalingService] Disconnected: " + reason);
                    if (listener != null) {
                        listener.onDisconnected(code, reason);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("[SignalingService] Error: " + ex.getMessage());
                    if (listener != null) {
                        listener.onError(ex);
                    }
                }
            };

            wsClient.connectBlocking();
        } catch (URISyntaxException e) {
            System.err.println("[SignalingService] Invalid server URL: " + serverUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[SignalingService] Connection interrupted");
        }
    }

    /**
     * Send a JSON message to the signaling server.
     */
    public void send(JSONObject message) {
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(message.toString());
        } else {
            System.err.println("[SignalingService] Cannot send — not connected");
        }
    }

    /**
     * Close the WebSocket connection.
     */
    public void disconnect() {
        if (wsClient != null) {
            try {
                wsClient.closeBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isConnected() {
        return wsClient != null && wsClient.isOpen();
    }
}
