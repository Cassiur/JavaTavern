package com.zcz.javatavern.stream;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents one complete remote-stream request session.
 *
 * Each call to startStreaming() creates a new StreamSession. Callbacks use
 * the session reference for identity checks: a stale callback from an old
 * session is silently ignored. This prevents a late onComplete/onError from
 * a previous connection from clearing, cancelling, or persisting on behalf
 * of a newer session.
 *
 * Pure-Java — no Android types. Fully testable on the JVM.
 *
 * Thread-safety:
 *   - cancel() may be called from any thread.
 *   - complete(), stop(), error() may be called from any thread; only the
 *     first terminal call wins (at-most-once semantics from StreamAccumulator).
 *   - persistOnce() is idempotent: the first caller executes the persister on
 *     a background thread; subsequent calls are no-ops.
 */
public final class StreamSession {

    /** Opaque call handle that can cancel the underlying HTTP connection. */
    public interface CallHandle {
        void cancel();
    }

    /** Listener for results that must be delivered on a specific thread. */
    public interface ResultListener {
        /** Called when the terminal text is ready to display. Main thread. */
        void onTerminalText(long operationId, String text, long createdAt,
                StreamAccumulator.Status status);

        /** Called after the message has been persisted. Main thread. */
        void onPersisted(long operationId, long rowId, long createdAt);
    }

    private final StreamAccumulator accumulator;
    private final String characterId;
    private final StreamPersister persister;
    private final Runnable mainThreadRunner; // schedules tasks on the main thread
    private volatile CallHandle callHandle;

    // Ensures persist() runs at most once even if complete/stop race.
    private final AtomicBoolean persistClaimed = new AtomicBoolean(false);

    public StreamSession(
            long operationId,
            long createdAt,
            String characterId,
            StreamPersister persister,
            Runnable mainThreadRunner
    ) {
        this.accumulator = new StreamAccumulator(operationId, createdAt);
        this.characterId = characterId;
        this.persister = persister;
        this.mainThreadRunner = mainThreadRunner;
    }

    /** Returns the character ID this session persists to. */
    public String getCharacterId() {
        return characterId;
    }

    /** The session operation ID (matches accumulator operationId). */
    public long operationId() {
        return accumulator.current().operationId;
    }

    /** Returns the current accumulator state snapshot. */
    public StreamAccumulator.State currentState() {
        return accumulator.current();
    }

    /** Associates the live HTTP call handle so cancel() can stop the network. */
    public void setCallHandle(CallHandle handle) {
        this.callHandle = handle;
    }

    /**
     * Appends a delta. No-op if the session is already terminal.
     * Returns the updated State.
     */
    public StreamAccumulator.State appendDelta(String delta) {
        return accumulator.appendDelta(delta);
    }

    /**
     * Marks successful completion and triggers persist-once.
     * Returns non-null on the first winning call; null if already terminal.
     */
    public StreamAccumulator.State complete(ResultListener listener, String emptyFallback) {
        StreamAccumulator.State terminal = accumulator.complete();
        if (terminal != null) {
            String text = finalText(terminal.text, emptyFallback);
            notifyTerminal(terminal, text, listener);
            persistOnce(text, terminal, listener);
        }
        return terminal;
    }

    /**
     * Marks explicit user stop and triggers persist-once.
     * Returns non-null on first winning call; null if already terminal.
     */
    public StreamAccumulator.State stop(ResultListener listener,
            String emptyFallback, String stoppedMarker) {
        StreamAccumulator.State terminal = accumulator.stop();
        if (terminal != null) {
            String raw = terminal.text.trim();
            String text = raw.isEmpty()
                    ? emptyFallback
                    : raw + stoppedMarker;
            notifyTerminal(terminal, text, listener);
            persistOnce(text, terminal, listener);
        }
        return terminal;
    }

    /**
     * Marks failure. Does not persist (error is not a successful reply).
     * Returns non-null on first winning call; null if already terminal.
     */
    public StreamAccumulator.State error(String errorMessage, ResultListener listener) {
        StreamAccumulator.State terminal = accumulator.error(errorMessage);
        if (terminal != null) {
            notifyTerminal(terminal, terminal.errorMessage, listener);
        }
        return terminal;
    }

    /**
     * Cancels the underlying HTTP call. Safe to call multiple times.
     * Does NOT transition the accumulator state; a stale onComplete/onError
     * may still arrive but will find the accumulator already terminal.
     */
    public void cancel() {
        CallHandle h = callHandle;
        if (h != null) {
            h.cancel();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String finalText(String raw, String emptyFallback) {
        String trimmed = raw == null ? "" : raw.trim();
        return trimmed.isEmpty() ? emptyFallback : trimmed;
    }

    /**
     * Notifies the listener of the terminal text. Called on whatever thread
     * the terminal method was invoked on — callers are expected to post to
     * the main thread themselves (ViewModel does this via mainHandler.post).
     */
    private void notifyTerminal(StreamAccumulator.State terminal, String displayText,
            ResultListener listener) {
        if (listener != null) {
            listener.onTerminalText(terminal.operationId, displayText,
                    terminal.createdAt, terminal.status);
        }
    }

    /**
     * Guarantees the persister is called at most once per session.
     * Runs the persister synchronously on the calling thread (expected to be
     * a background thread), then posts the row ID back via the listener.
     *
     * In ViewModel usage the caller is mainHandler.post(); persistence is
     * then dispatched to dbExecutor before posting the result back to main.
     * In tests this is called directly to verify exact-once behavior.
     */
    public void persistOnce(String text, StreamAccumulator.State terminal,
            ResultListener listener) {
        if (!terminal.shouldPersist()) {
            return;
        }
        if (!persistClaimed.compareAndSet(false, true)) {
            return; // another call already won the race
        }
        // Actual I/O is expected to be dispatched to a background executor
        // by the caller (ViewModel). Here we just invoke the persister.
        long rowId = persister.persist(characterId, text, terminal.createdAt);
        if (listener != null) {
            listener.onPersisted(terminal.operationId, rowId, terminal.createdAt);
        }
    }
}
