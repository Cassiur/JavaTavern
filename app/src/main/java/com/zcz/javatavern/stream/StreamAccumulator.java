package com.zcz.javatavern.stream;

/**
 * Pure-Java stream state machine.
 *
 * Thread-safety: appendDelta and all terminal methods may be called from any
 * thread. A terminal transition is accepted exactly once; subsequent calls to
 * any terminal method are no-ops. appendDelta after terminal is a no-op.
 *
 * No Android types — fully testable on the JVM.
 */
public final class StreamAccumulator {

    public enum Status { CONNECTING, STREAMING, COMPLETED, STOPPED, ERROR }

    public static final class State {
        public final long operationId;
        public final Status status;
        public final String text;
        public final long createdAt;
        public final String errorMessage;

        State(long operationId, Status status, String text,
                long createdAt, String errorMessage) {
            this.operationId = operationId;
            this.status = status;
            this.text = text;
            this.createdAt = createdAt;
            this.errorMessage = errorMessage;
        }

        public boolean isTerminal() {
            return status == Status.COMPLETED
                    || status == Status.STOPPED
                    || status == Status.ERROR;
        }

        /** True when this state should be persisted as an assistant message. */
        public boolean shouldPersist() {
            return status == Status.COMPLETED || status == Status.STOPPED;
        }
    }

    private volatile State current;
    private final StringBuilder buffer = new StringBuilder();
    // Guards buffer mutations and buffer→state promotions.
    private final Object lock = new Object();

    public StreamAccumulator(long operationId, long createdAt) {
        current = new State(operationId, Status.CONNECTING, "", createdAt, "");
    }

    /** Returns the latest state snapshot. */
    public State current() {
        return current;
    }

    /**
     * Appends a text delta. No-op if already terminal.
     * Returns the new State so callers can publish it.
     */
    public State appendDelta(String delta) {
        if (delta == null || delta.isEmpty()) {
            return current;
        }
        synchronized (lock) {
            if (current.isTerminal()) {
                return current;
            }
            buffer.append(delta);
            current = new State(current.operationId, Status.STREAMING,
                    buffer.toString(), current.createdAt, "");
            return current;
        }
    }

    /**
     * Marks successful completion.
     * Returns the terminal State if this call won the race (caller MUST
     * persist exactly once), or null if already terminal (must NOT persist).
     */
    public State complete() {
        return transitionToTerminal(Status.COMPLETED, "");
    }

    /**
     * Marks explicit user stop. Same return-value contract as complete().
     */
    public State stop() {
        return transitionToTerminal(Status.STOPPED, "");
    }

    /**
     * Marks failure. Does not fabricate a successful reply; errorMessage is
     * set on the returned State. Returns null if already terminal.
     */
    public State error(String message) {
        synchronized (lock) {
            if (current.isTerminal()) {
                return null;
            }
            current = new State(current.operationId, Status.ERROR,
                    buffer.toString(), current.createdAt,
                    message == null ? "" : message);
            return current;
        }
    }

    private State transitionToTerminal(Status target, String errorMessage) {
        synchronized (lock) {
            if (current.isTerminal()) {
                return null;
            }
            current = new State(current.operationId, target,
                    buffer.toString(), current.createdAt, errorMessage);
            return current;
        }
    }
}
