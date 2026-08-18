package com.zcz.javatavern.network;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ImageDataUrlEncoder {
    public String encode(String path, String mimeType) throws IOException {
        byte[] bytes;
        try (InputStream inputStream = new FileInputStream(path);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readBytes);
            }
            bytes = outputStream.toByteArray();
        }
        return "data:" + mimeType + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
