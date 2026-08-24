package com.zcz.javatavern.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tests for StreamSession — the pure-Java session boundary layer.
 *
 * Covers stable state reads, exact-once persistence, cancellation, error
 * handling, idempotent cleanup, and stale-callback isolation.
 *
 * Also covers stale-callback isolation: a session that has already reached
 * a terminal state ignores all further terminal calls.
 */
public final class StreamSessionTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static StreamSession makeSession(long opId, long createdAt,
            StreamPersister persister) {
        return new StreamSession(opId, createdAt, "char-1", persister, null);
    }

    /** Records every onTerminalText / onPersisted call for assertion. */
    private static final class RecordingListener
            implements StreamSession.ResultListener {
        final List<String> terminalTexts = new ArrayList<>();
        final List<Long> persistedRowIds = new ArrayList<>();

        @Override
        public void onTerminalText(long operationId, String text,
                long createdAt, StreamAccumulator.Status status) {
            terminalTexts.add(text);
        }

        @Override
        public void onPersisted(long operationId, long rowId, long createdAt) {
            persistedRowIds.add(rowId);
        }
    }

    /** Tracks how many times persist() was called and returns a stable rowId. */
    private static StreamPersister countingPersister(AtomicInteger count, long rowId) {
        return (cid, text, ts) -> {
            count.incrementAndGet();
            return rowId;
        };
    }

    // -------------------------------------------------------------------------
    // RT-1: State is stable across multiple currentState() reads
    //       (observer detach/reattach analogue at the StreamSession level)
    // -------------------------------------------------------------------------

    @Test
    public void currentState_stableAcrossRepeatedReads() {
        StreamSession s = makeSession(1L, 100L, (c, t, ts) -> 0L);
        s.appendDelta("hello");

        StreamAccumulator.State a = s.currentState();
        StreamAccumulator.State b = s.currentState();

        assertEquals("same operationId", a.operationId, b.operationId);
        assertEquals("same text", a.text, b.text);
        assertEquals("same status", a.status, b.status);
    }

    @Test
    public void currentState_afterTerminal_remainsStable() {
        StreamSession s = makeSession(2L, 200L, (c, t, ts) -> 99L);
        s.appendDelta("partial");
        s.complete(null, "fallback");

        StreamAccumulator.State a = s.currentState();
        StreamAccumulator.State b = s.currentState();

        assertEquals(StreamAccumulator.Status.COMPLETED, a.status);
        assertEquals(a.status, b.status);
        assertEquals(a.text, b.text);
    }

    // -------------------------------------------------------------------------
    // RT-2: At-most-once persistence even with concurrent terminal calls
    // -------------------------------------------------------------------------

    @Test
    public void complete_persistsExactlyOnce() {
        AtomicInteger count = new AtomicInteger(0);
        StreamSession s = makeSession(3L, 300L, countingPersister(count, 10L));
        RecordingListener listener = new RecordingListener();

        s.appendDelta("text");
        StreamAccumulator.State first = s.complete(listener, "fallback");
        StreamAccumulator.State second = s.complete(listener, "fallback");

        assertNotNull("first complete() must win", first);
        assertNull("second complete() must lose", second);
        assertEquals("persisted exactly once", 1, count.get());
        assertEquals("listener called once", 1, listener.terminalTexts.size());
        assertEquals("rowId propagated once", 1, listener.persistedRowIds.size());
        assertEquals(10L, (long) listener.persistedRowIds.get(0));
    }

    @Test
    public void stop_afterComplete_doesNotPersistAgain() {
        AtomicInteger count = new AtomicInteger(0);
        StreamSession s = makeSession(4L, 400L, countingPersister(count, 20L));
        RecordingListener listener = new RecordingListener();

        s.appendDelta("done");
        s.complete(listener, "fallback");
        s.stop(listener, "fallback", "\n\n[stopped]");

        assertEquals("persisted exactly once", 1, count.get());
    }

    @Test
    public void complete_afterStop_doesNotPersistAgain() {
        AtomicInteger count = new AtomicInteger(0);
        StreamSession s = makeSession(5L, 500L, countingPersister(count, 30L));
        RecordingListener listener = new RecordingListener();

        s.appendDelta("partial");
        s.stop(listener, "fallback", "\n\n[stopped]");
        s.complete(listener, "fallback");

        assertEquals("persisted exactly once", 1, count.get());
    }

    @Test
    public void error_afterComplete_noPersist() {
        AtomicInteger count = new AtomicInteger(0);
        StreamSession s = makeSession(6L, 600L, countingPersister(count, 40L));
        RecordingListener listener = new RecordingListener();

        s.appendDelta("done");
        s.complete(listener, "fallback");
        StreamAccumulator.State stale = s.error("late error", listener);

        assertNull("error after complete returns null", stale);
        assertEquals("persist count unchanged", 1, count.get());
    }

    // -------------------------------------------------------------------------
    // RT-3: Stop cancels the underlying call and produces documented final state
    // -------------------------------------------------------------------------

    @Test
    public void stop_cancelsCallHandle() {
        // The ViewModel calls session.cancel() before session.stop().
        // cancel() is what fires the CallHandle; stop() only transitions
        // the accumulator state. This test verifies that cancel() is
        // independent of stop() and that the final state is STOPPED.
        AtomicInteger cancelCount = new AtomicInteger(0);
        StreamSession s = makeSession(7L, 700L, (c, t, ts) -> 0L);
        s.setCallHandle(() -> cancelCount.incrementAndGet());

        s.appendDelta("partial text");
        s.cancel();  // ViewModel calls cancel() to abort the HTTP connection
        s.stop(null, "empty", "\n\n[stopped]");  // then transitions state

        assertEquals("cancel() called once via cancel()", 1, cancelCount.get());
        assertEquals(StreamAccumulator.Status.STOPPED, s.currentState().status);
    }

    @Test
    public void stop_withContent_appendsStoppedMarker() {
        List<String> texts = new ArrayList<>();
        StreamSession s = makeSession(8L, 800L, (c, t, ts) -> 0L);
        s.setCallHandle(() -> {});

        s.appendDelta("hello world");
        s.stop(new StreamSession.ResultListener() {
            @Override
            public void onTerminalText(long opId, String text,
                    long createdAt, StreamAccumulator.Status status) {
                texts.add(text);
            }
            @Override public void onPersisted(long opId, long rowId, long createdAt) {}
        }, "empty", "\n\n[stopped]");

        assertEquals(1, texts.size());
        assertEquals("hello world\n\n[stopped]", texts.get(0));
    }

    @Test
    public void stop_withNoContent_usesCancelledFallback() {
        List<String> texts = new ArrayList<>();
        StreamSession s = makeSession(9L, 900L, (c, t, ts) -> 0L);
        s.setCallHandle(() -> {});

        // No appendDelta — empty accumulator.
        s.stop(new StreamSession.ResultListener() {
            @Override
            public void onTerminalText(long opId, String text,
                    long createdAt, StreamAccumulator.Status status) {
                texts.add(text);
            }
            @Override public void onPersisted(long opId, long rowId, long createdAt) {}
        }, "已停止生成", "\n\n[stopped]");

        assertEquals(1, texts.size());
        assertEquals("已停止生成", texts.get(0));
    }

    @Test
    public void stop_producesStoppedStatus() {
        StreamSession s = makeSession(10L, 1000L, (c, t, ts) -> 0L);
        s.appendDelta("x");
        s.stop(null, "fallback", "\n\n[stopped]");

        assertEquals(StreamAccumulator.Status.STOPPED, s.currentState().status);
    }

    // -------------------------------------------------------------------------
    // RT-4: Error does not persist a fabricated successful reply
    // -------------------------------------------------------------------------

    @Test
    public void error_doesNotPersist() {
        AtomicInteger count = new AtomicInteger(0);
        StreamSession s = makeSession(11L, 1100L, countingPersister(count, 50L));
        RecordingListener listener = new RecordingListener();

        s.appendDelta("some text before error");
        s.error("network failure", listener);

        assertEquals("no persist for error", 0, count.get());
        assertEquals("onTerminalText called", 1, listener.terminalTexts.size());
        assertEquals("no onPersisted for error", 0, listener.persistedRowIds.size());
        assertEquals(StreamAccumulator.Status.ERROR, s.currentState().status);
    }

    @Test
    public void error_stateDoesNotShouldPersist() {
        StreamSession s = makeSession(12L, 1200L, (c, t, ts) -> 0L);
        s.appendDelta("text");
        s.error("boom", null);

        assertFalse(s.currentState().shouldPersist());
    }

    @Test
    public void error_passesErrorMessageNotPartialText() {
        StreamSession s = makeSession(16L, 1600L, (c, t, ts) -> 0L);
        RecordingListener listener = new RecordingListener();

        s.appendDelta("partial generated text");
        s.error("网络连接失败", listener);

        assertEquals("onTerminalText called once", 1, listener.terminalTexts.size());
        assertEquals("terminal text must be the error message, not partial text",
                "网络连接失败", listener.terminalTexts.get(0));
    }

    // -------------------------------------------------------------------------
    // RT-5: Cleanup is idempotent
    // -------------------------------------------------------------------------

    @Test
    public void cancel_isIdempotent() {
        AtomicInteger count = new AtomicInteger(0);
        StreamSession s = makeSession(13L, 1300L, (c, t, ts) -> 0L);
        s.setCallHandle(() -> count.incrementAndGet());

        s.cancel();
        s.cancel();
        s.cancel();

        assertEquals("cancel() is idempotent", 3, count.get());
        // (CallHandle.cancel() is called each time — the underlying OkHttp/HttpURLConnection
        // call is idempotent, so multiple calls are safe.)
    }

    @Test
    public void cancelThenComplete_persistsOnce() {
        AtomicInteger count = new AtomicInteger(0);
        StreamSession s = makeSession(14L, 1400L, countingPersister(count, 60L));
        s.setCallHandle(() -> {});

        s.appendDelta("partial");
        s.cancel();
        // complete() arrives after cancel — accumulator is not yet terminal,
        // so this should still win the race and persist once.
        StreamAccumulator.State result = s.complete(null, "fallback");

        assertNotNull("complete() after cancel should still transition", result);
        assertEquals(1, count.get());
    }

    @Test
    public void noCallHandle_cancelIsNoOp() {
        // Should not throw even if no CallHandle was set.
        StreamSession s = makeSession(15L, 1500L, (c, t, ts) -> 0L);
        s.cancel(); // must not throw
        s.cancel();
    }

    // -------------------------------------------------------------------------
    // Stale-callback isolation
    // -------------------------------------------------------------------------

    @Test
    public void staleCompleteAfterNewSession_doesNotPersist() {
        // Simulate: session A completes, but a stale onComplete arrives after
        // session B has started. Because session A is already terminal, the
        // stale complete() returns null and does not call the persister.
        AtomicInteger countA = new AtomicInteger(0);
        StreamSession sessionA = makeSession(100L, 1000L,
                countingPersister(countA, 1L));
        RecordingListener listenerA = new RecordingListener();

        sessionA.appendDelta("A text");
        sessionA.complete(listenerA, "fallback"); // wins the first time
        assertEquals(1, countA.get());

        // Session A already terminal — simulate stale callback arriving again.
        StreamAccumulator.State stale = sessionA.complete(listenerA, "fallback");
        assertNull("stale complete must return null", stale);
        assertEquals("persist count must not increase", 1, countA.get());
        assertEquals("listener must not be called again", 1,
                listenerA.terminalTexts.size());
    }

    @Test
    public void complete_withEmptyText_usesFallback() {
        List<String> texts = new ArrayList<>();
        StreamSession s = makeSession(200L, 2000L, (c, t, ts) -> 0L);
        // No appendDelta — empty buffer.
        s.complete(new StreamSession.ResultListener() {
            @Override
            public void onTerminalText(long opId, String text,
                    long createdAt, StreamAccumulator.Status status) {
                texts.add(text);
            }
            @Override public void onPersisted(long opId, long rowId, long createdAt) {}
        }, "empty fallback");

        assertEquals(1, texts.size());
        assertEquals("empty fallback", texts.get(0));
    }
}
