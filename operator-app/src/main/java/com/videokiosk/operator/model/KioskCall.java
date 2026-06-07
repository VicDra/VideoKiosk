package com.videokiosk.operator.model;

import java.time.LocalDateTime;

/**
 * Represents an incoming call request from a kiosk client.
 */
public class KioskCall {

    public enum CallStatus {
        INCOMING,
        ACCEPTED,
        REJECTED,
        IN_CALL
    }

    private String clientId;
    private CallStatus status;
    private LocalDateTime timestamp;

    public KioskCall(String clientId) {
        this.clientId = clientId;
        this.status = CallStatus.INCOMING;
        this.timestamp = LocalDateTime.now();
    }

    // ---------------------------------------------------------------------------
    // Getters & Setters
    // ---------------------------------------------------------------------------

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "KioskCall{clientId='" + clientId + "', status=" + status +
                ", timestamp=" + timestamp + '}';
    }
}
