package com.zcz.javatavern.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MessageImageLoader implements AutoCloseable {
    private static final int TARGET_EDGE_PX = 720;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    public MessageImageLoader() {
        int maxKilobytes = (int) (Runtime.getRuntime().maxMemory() / 1024L);
        cache = new LruCache<>(Math.max(4 * 1024, maxKilobytes / 12)) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getAllocationByteCount() / 1024;
            }
        };
    }

    public void load(String path, Consumer<Bitmap> callback) {
        Bitmap cached = cache.get(path);
        if (cached != null) {
            callback.accept(cached);
            return;
        }
        executor.execute(() -> {
            Bitmap bitmap = decodeSampled(path);
            if (bitmap != null) {
                cache.put(path, bitmap);
            }
            mainHandler.post(() -> callback.accept(bitmap));
        });
    }

    private Bitmap decodeSampled(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sampleSize = 1;
        while (Math.max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize)
                > TARGET_EDGE_PX * 2) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeFile(path, options);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        cache.evictAll();
    }
}
