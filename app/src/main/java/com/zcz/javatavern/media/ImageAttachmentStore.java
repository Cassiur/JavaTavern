package com.zcz.javatavern.media;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public final class ImageAttachmentStore {
    private static final int MAX_EDGE_PX = 1_600;
    private static final long MAX_INPUT_BYTES = 15L * 1024L * 1024L;
    private final Context context;

    public ImageAttachmentStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public Attachment importImage(Uri sourceUri) throws IOException {
        validateSize(sourceUri);
        Bitmap bitmap = decode(sourceUri);
        if (bitmap == null) {
            throw new IOException("无法解码所选图片");
        }
        Bitmap scaledBitmap = scale(bitmap);
        File imageDirectory = new File(context.getFilesDir(), "message_images");
        if (!imageDirectory.exists() && !imageDirectory.mkdirs()) {
            recycle(bitmap, scaledBitmap);
            throw new IOException("无法创建图片目录");
        }
        File outputFile = new File(imageDirectory, UUID.randomUUID() + ".jpg");
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            if (!scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)) {
                throw new IOException("图片压缩失败");
            }
        } finally {
            recycle(bitmap, scaledBitmap);
        }
        return new Attachment(outputFile.getAbsolutePath(), "image/jpeg");
    }

    public void delete(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File imageDirectory = new File(context.getFilesDir(), "message_images");
        File target = new File(path);
        try {
            String directoryPath = imageDirectory.getCanonicalPath() + File.separator;
            if (target.getCanonicalPath().startsWith(directoryPath)) {
                target.delete();
            }
        } catch (IOException ignored) {
        }
    }

    private void validateSize(Uri sourceUri) throws IOException {
        try (ParcelFileDescriptor descriptor = context.getContentResolver()
                .openFileDescriptor(sourceUri, "r")) {
            if (descriptor != null) {
                long size = descriptor.getStatSize();
                if (size > MAX_INPUT_BYTES) {
                    throw new IOException("图片不能超过 15 MB");
                }
            }
        }
    }

    private Bitmap decode(Uri sourceUri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.Source source = ImageDecoder.createSource(resolver, sourceUri);
            return ImageDecoder.decodeBitmap(source, (decoder, info, imageSource) -> {
                int width = info.getSize().getWidth();
                int height = info.getSize().getHeight();
                float scale = Math.min(1f, (float) MAX_EDGE_PX / Math.max(width, height));
                if (scale < 1f) {
                    decoder.setTargetSize(
                            Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale))
                    );
                }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream inputStream = resolver.openInputStream(sourceUri)) {
            BitmapFactory.decodeStream(inputStream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("图片尺寸无效");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight);
        try (InputStream inputStream = resolver.openInputStream(sourceUri)) {
            return BitmapFactory.decodeStream(inputStream, null, options);
        }
    }

    private int calculateSampleSize(int width, int height) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > MAX_EDGE_PX * 2) {
            sampleSize *= 2;
        }
        return sampleSize;
    }

    private Bitmap scale(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min(1f, (float) MAX_EDGE_PX / Math.max(width, height));
        if (scale >= 1f) {
            return bitmap;
        }
        return Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)),
                true
        );
    }

    private void recycle(Bitmap original, Bitmap scaled) {
        if (scaled != original) {
            scaled.recycle();
        }
        original.recycle();
    }

    public static final class Attachment {
        private final String path;
        private final String mimeType;

        public Attachment(String path, String mimeType) {
            this.path = path;
            this.mimeType = mimeType;
        }

        public String getPath() {
            return path;
        }

        public String getMimeType() {
            return mimeType;
        }
    }
}
