package com.zcz.javatavern.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RelativeTimeTest {
    private static final long NOW = 1_700_000_000_000L;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    public void nonPositiveTimestampIsBlank() {
        assertEquals("", RelativeTime.format(0L, NOW));
        assertEquals("", RelativeTime.format(-1L, NOW));
    }

    @Test
    public void subMinuteIsJustNow() {
        assertEquals("刚刚", RelativeTime.format(NOW, NOW));
        assertEquals("刚刚", RelativeTime.format(NOW - 59_999L, NOW));
    }

    @Test
    public void futureTimestampClampsToJustNow() {
        assertEquals("刚刚", RelativeTime.format(NOW + 60_000L, NOW));
    }

    @Test
    public void minutes() {
        assertEquals("1 分钟前", RelativeTime.format(NOW - MINUTE, NOW));
        assertEquals("3 分钟前", RelativeTime.format(NOW - 3 * MINUTE, NOW));
    }

    @Test
    public void hours() {
        assertEquals("1 小时前", RelativeTime.format(NOW - HOUR, NOW));
        assertEquals("2 小时前", RelativeTime.format(NOW - 2 * HOUR, NOW));
    }

    @Test
    public void days() {
        assertEquals("1 天前", RelativeTime.format(NOW - DAY, NOW));
        assertEquals("6 天前", RelativeTime.format(NOW - 6 * DAY, NOW));
    }

    @Test
    public void weekOrOlderIsStable() {
        assertEquals("更早", RelativeTime.format(NOW - 7 * DAY, NOW));
        assertEquals("更早", RelativeTime.format(NOW - 30 * DAY, NOW));
    }
}
