package com.zcz.javatavern.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * App-wide shared executors, replacing the scattered per-class
 * {@code Executors.newSingleThreadExecutor() / newCachedThreadPool()} pools.
 *
 * <p>A single shared set of
 * pools gives predictable thread counts and lets the OS manage their lifecycle
 * (they live for the whole process — never call {@code shutdown()} on them).
 *
 * <ul>
 *   <li>{@link #diskIo()}  — single worker, serializes database/file writes.</li>
 *   <li>{@link #network()} — bounded pool for model requests &amp; connection tests.</li>
 *   <li>{@link #image()}   — fixed two workers for image import/decode.</li>
 * </ul>
 */
public final class AppExecutors {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static volatile AppExecutors instance;

    private final ExecutorService diskIo;
    private final ThreadPoolExecutor network;
    private final ExecutorService image;

    private AppExecutors() {
        diskIo = Executors.newSingleThreadExecutor(namedFactory("tavern-disk"));
        network = new ThreadPoolExecutor(
                Math.max(2, CPU_COUNT / 2),
                Math.max(4, CPU_COUNT),
                30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                namedFactory("tavern-net")
        );
        network.allowCoreThreadTimeOut(true);
        image = Executors.newFixedThreadPool(2, namedFactory("tavern-image"));
    }

    public static AppExecutors get() {
        AppExecutors current = instance;
        if (current == null) {
            synchronized (AppExecutors.class) {
                current = instance;
                if (current == null) {
                    current = new AppExecutors();
                    instance = current;
                }
            }
        }
        return current;
    }

    /** Serialized background I/O (SQLite, file writes). */
    public ExecutorService diskIo() {
        return diskIo;
    }

    /** Bounded pool for network calls. */
    public ExecutorService network() {
        return network;
    }

    /** Fixed pool for image import and bitmap decoding. */
    public ExecutorService image() {
        return image;
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
