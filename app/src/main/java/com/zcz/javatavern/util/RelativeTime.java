package com.zcz.javatavern.util;

/**
 * Formats an epoch-millisecond timestamp as a short, human-readable relative
 * time ("刚刚", "3 分钟前", …). Pure Java so it can be unit-tested without an
 * Android runtime; {@code now} is passed explicitly for deterministic tests.
 */
public final class RelativeTime {
    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;

    private RelativeTime() {
    }

    /**
     * Returns a relative-time label for {@code timestamp} as seen from
     * {@code now}, or an empty string when {@code timestamp <= 0}.
     */
    public static String format(long timestamp, long now) {
        if (timestamp <= 0) {
            return "";
        }
        long elapsed = now - timestamp;
        if (elapsed < 0) {
            elapsed = 0;
        }
        if (elapsed < MINUTE_MS) {
            return "刚刚";
        }
        if (elapsed < HOUR_MS) {
            return (elapsed / MINUTE_MS) + " 分钟前";
        }
        if (elapsed < DAY_MS) {
            return (elapsed / HOUR_MS) + " 小时前";
        }
        long days = elapsed / DAY_MS;
        if (days < 7) {
            return days + " 天前";
        }
        return "更早";
    }
}
