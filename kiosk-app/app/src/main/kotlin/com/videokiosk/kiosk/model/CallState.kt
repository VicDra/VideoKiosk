package com.videokiosk.kiosk.model

/**
 * Sealed class representing all possible states of a kiosk video call.
 */
sealed class CallState {

    /** No active call; the kiosk is on the waiting screen. */
    object Idle : CallState()

    /**
     * The kiosk has requested a call but the operator is busy.
     * @param position 1-based position in the operator's queue.
     */
    data class Queued(val position: Int) : CallState()

    /** A call request has been sent and is awaiting the operator's response. */
    object Calling : CallState()

    /** The operator has accepted; a WebRTC session is active. */
    object InCall : CallState()

    /**
     * An unrecoverable error occurred.
     * @param message Human-readable description of the error.
     */
    data class Error(val message: String) : CallState()
}
