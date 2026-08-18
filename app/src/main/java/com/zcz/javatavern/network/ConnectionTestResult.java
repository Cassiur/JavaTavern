package com.zcz.javatavern.network;

public final class ConnectionTestResult {
    private final boolean successful;
    private final String message;
    private final long latencyMillis;

    public ConnectionTestResult(boolean successful, String message, long latencyMillis) {
        this.successful = successful;
        this.message = message;
        this.latencyMillis = latencyMillis;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getMessage() {
        return message;
    }

    public long getLatencyMillis() {
        return latencyMillis;
    }
}
