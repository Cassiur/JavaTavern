package com.zcz.javatavern.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for the pure-Java StreamAccumulator state machine.
 *
 * These tests cover the accumulator's internal state transitions only.
 * Full RT-1 through RT-5 coverage (observer reattachment, at-most-once
 * persistence, call cancellation, stale-callback isolation, and idempotent
 * cleanup) is in StreamSessionTest.
 */
public final class StreamAccumulatorTest {

    // -------------------------------------------------------------------------
    // Basic state transitions
    // -------------------------------------------------------------------------

    @Test
    public void initialStateIsConnecting() {
        StreamAccumulator acc = new StreamAccumulator(1L, 100L);
        StreamAccumulator.State s = acc.current();

        assertEquals(StreamAccumulator.Status.CONNECTING, s.status);
        assertEquals("", s.text);
        assertEquals(100L, s.createdAt);
        assertFalse(s.isTerminal());
        assertFalse(s.shouldPersist());
    }

    @Test
    public void appendDeltaTransitionsToStreaming() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        StreamAccumulator.State s = acc.appendDelta("hello");

        assertEquals(StreamAccumulator.Status.STREAMING, s.status);
        assertEquals("hello", s.text);
        assertFalse(s.isTerminal());
    }

    @Test
    public void appendDeltaAccumulates() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        acc.appendDelta("foo");
        StreamAccumulator.State s = acc.appendDelta("bar");

        assertEquals("foobar", s.text);
    }

    @Test
    public void completeTransitionsToCompleted() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        acc.appendDelta("text");
        StreamAccumulator.State terminal = acc.complete();

        assertNotNull("complete() must return non-null on first call", terminal);
        assertEquals(StreamAccumulator.Status.COMPLETED, terminal.status);
        assertEquals("text", terminal.text);
        assertTrue(terminal.isTerminal());
        assertTrue(terminal.shouldPersist());
    }

    @Test
    public void stopTransitionsToStopped() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        acc.appendDelta("partial");
        StreamAccumulator.State terminal = acc.stop();

        assertNotNull("stop() must return non-null on first call", terminal);
        assertEquals(StreamAccumulator.Status.STOPPED, terminal.status);
        assertEquals("partial", terminal.text);
        assertTrue(terminal.shouldPersist());
    }

    @Test
    public void errorTransitionsToError() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        acc.appendDelta("some");
        StreamAccumulator.State terminal = acc.error("timeout");

        assertNotNull(terminal);
        assertEquals(StreamAccumulator.Status.ERROR, terminal.status);
        assertEquals("timeout", terminal.errorMessage);
        assertTrue(terminal.isTerminal());
        assertFalse("error state must not be persisted", terminal.shouldPersist());
    }

    // -------------------------------------------------------------------------
    // RT-1: State survives repeated reads (observer detach/reattach analogue)
    // -------------------------------------------------------------------------

    @Test
    public void currentReturnsSameStateOnRepeatedReads() {
        StreamAccumulator acc = new StreamAccumulator(42L, 999L);
        acc.appendDelta("x");
        StreamAccumulator.State a = acc.current();
        StreamAccumulator.State b = acc.current();

        assertEquals(a.status, b.status);
        assertEquals(a.text, b.text);
        assertEquals(a.operationId, b.operationId);
    }

    // -------------------------------------------------------------------------
    // RT-2: At-most-once persistence — repeated terminal calls return null
    // -------------------------------------------------------------------------

    @Test
    public void completeTwiceSecondCallReturnsNull() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        assertNotNull(acc.complete());
        assertNull("second complete() must return null", acc.complete());
    }

    @Test
    public void stopAfterCompleteReturnsNull() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        assertNotNull(acc.complete());
        assertNull("stop() after complete() must return null", acc.stop());
    }

    @Test
    public void completeAfterStopReturnsNull() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        assertNotNull(acc.stop());
        assertNull("complete() after stop() must return null", acc.complete());
    }

    @Test
    public void errorAfterCompleteReturnsNull() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        assertNotNull(acc.complete());
        assertNull("error() after complete() must return null", acc.error("late"));
    }

    @Test
    public void completeAfterErrorReturnsNull() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        assertNotNull(acc.error("boom"));
        assertNull("complete() after error() must return null", acc.complete());
    }

    // -------------------------------------------------------------------------
    // RT-3: Stop produces the documented final state
    // -------------------------------------------------------------------------

    @Test
    public void stopPreservesAccumulatedText() {
        StreamAccumulator acc = new StreamAccumulator(1L, 123L);
        acc.appendDelta("line1\n");
        acc.appendDelta("line2");
        StreamAccumulator.State s = acc.stop();

        assertNotNull(s);
        assertEquals("line1\nline2", s.text);
        assertEquals(123L, s.createdAt);
        assertEquals(StreamAccumulator.Status.STOPPED, s.status);
    }

    // -------------------------------------------------------------------------
    // RT-4: Error does not persist a fabricated successful reply
    // -------------------------------------------------------------------------

    @Test
    public void errorStateDoesNotShouldPersist() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        acc.appendDelta("accumulated text");
        StreamAccumulator.State terminal = acc.error("network failure");

        assertNotNull(terminal);
        assertFalse("error() result must not trigger persistence", terminal.shouldPersist());
    }

    // -------------------------------------------------------------------------
    // RT-5: Cleanup is idempotent — appendDelta after terminal is a no-op
    // -------------------------------------------------------------------------

    @Test
    public void appendDeltaAfterCompleteIsNoOp() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        acc.appendDelta("original");
        acc.complete();

        StreamAccumulator.State s = acc.appendDelta("late delta");
        assertEquals(StreamAccumulator.Status.COMPLETED, s.status);
        assertEquals("original", s.text);
    }

    @Test
    public void appendDeltaAfterStopIsNoOp() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        acc.appendDelta("base");
        acc.stop();

        StreamAccumulator.State s = acc.appendDelta("ignored");
        assertEquals(StreamAccumulator.Status.STOPPED, s.status);
        assertEquals("base", s.text);
    }

    @Test
    public void multipleErrorCallsAreIdempotent() {
        StreamAccumulator acc = new StreamAccumulator(1L, 0L);
        StreamAccumulator.State first = acc.error("first error");
        StreamAccumulator.State second = acc.error("second error");

        assertNotNull(first);
        assertNull("second error() after terminal must return null", second);
        assertEquals("first error", acc.current().errorMessage);
    }

    // -------------------------------------------------------------------------
    // operationId propagation
    // -------------------------------------------------------------------------

    @Test
    public void operationIdPreservedThroughTransitions() {
        long opId = 77L;
        StreamAccumulator acc = new StreamAccumulator(opId, 0L);
        acc.appendDelta("x");
        StreamAccumulator.State terminal = acc.complete();

        assertNotNull(terminal);
        assertEquals(opId, terminal.operationId);
    }
}
