package com.zcz.javatavern.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Stores character-card portraits extracted from imported PNG cards.
 *
 * <p>Portraits are downscaled to at most 256px and written as PNG so transparency is
 * preserved. The resulting absolute path is what {@code CharacterProfile.avatar}
 * stores for imported characters.
 */
public final class AvatarStore {
    private static final int MAX_AVATAR_EDGE = 256;
    private final Context context;

    public AvatarStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public String storeFromBytes(byte[] imageBytes) throws IOException {
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        if (bitmap == null) {
            throw new IOException("无法解码角色卡图片");
        }
        Bitmap scaled = scale(bitmap);
        File avatarDirectory = new File(context.getFilesDir(), "avatars");
        if (!avatarDirectory.exists() && !avatarDirectory.mkdirs()) {
            recycle(bitmap, scaled);
            throw new IOException("无法创建头像目录");
        }
        File outputFile = new File(avatarDirectory, UUID.randomUUID() + ".png");
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("头像压缩失败");
            }
        } finally {
            recycle(bitmap, scaled);
        }
        return outputFile.getAbsolutePath();
    }

    public void delete(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        File avatarDirectory = new File(context.getFilesDir(), "avatars");
        File target = new File(path);
        try {
            String directoryPath = avatarDirectory.getCanonicalPath() + File.separator;
            if (target.getCanonicalPath().startsWith(directoryPath)) {
                // noinspection ResultOfMethodCallIgnored
                target.delete();
            }
        } catch (IOException ignored) {
        }
    }

    private Bitmap scale(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min(1f, (float) MAX_AVATAR_EDGE / Math.max(width, height));
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
}
