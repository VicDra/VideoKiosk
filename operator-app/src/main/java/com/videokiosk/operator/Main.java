package com.videokiosk.operator;

/**
 * Launcher class — required to avoid JavaFX module issues when packaging a fat JAR.
 * Simply delegates to {@link MainApp}.
 */
public class Main {

    public static void main(String[] args) {
        MainApp.main(args);
    }
}
