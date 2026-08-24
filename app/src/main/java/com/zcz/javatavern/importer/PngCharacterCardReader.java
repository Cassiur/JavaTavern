package com.zcz.javatavern.importer;

import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Locale;

/**
 * Reads SillyTavern character cards embedded inside PNG files.
 *
 * <p>ST character cards ship as PNG images whose {@code tEXt} / {@code iTXt}
 * chunks carry the card JSON (base64-encoded) under the keyword {@code chara}
 * (V1/V2) or {@code ccv3} (V3). The parser implements those interoperable
 * metadata conventions directly and does not depend on another client.
 */
public final class PngCharacterCardReader {
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G',
            0x0D, 0x0A, 0x1A, 0x0A
    };

    private PngCharacterCardReader() {
    }

    public static boolean isPng(byte[] bytes) {
        if (bytes == null || bytes.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PNG_SIGNATURE.length; index++) {
            if (bytes[index] != PNG_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts and returns the card JSON text from a PNG, or {@code null} when
     * no card payload is found. The returned text is already base64/URI decoded
     * and ready for {@code CharacterCardParser.parse(String)}.
     */
    public static String extractCardJson(byte[] pngBytes) {
        if (!isPng(pngBytes)) {
            return null;
        }
        int offset = PNG_SIGNATURE.length;
        int limit = pngBytes.length;
        // First pass: prefer well-known card keywords.
        String candidate = null;
        while (offset + 8 <= limit) {
            String type = ascii(pngBytes, offset + 4, 4);
            int dataStart = offset + 8;
            int dataEnd = checkedDataEnd(pngBytes, offset, limit);
            if (dataEnd < 0) {
                break;
            }
            if ("tEXt".equals(type) || "iTXt".equals(type)) {
                TextChunk chunk = parseTextChunk(type, pngBytes, dataStart, dataEnd);
                if (chunk != null) {
                    String keyword = chunk.keyword.toLowerCase(Locale.ROOT);
                    if ("chara".equals(keyword) || "character".equals(keyword)
                            || "chara_card_v2".equals(keyword) || "card".equals(keyword)
                            || "ccv3".equals(keyword)) {
                        String json = decodeCardText(chunk.text);
                        if (json != null) {
                            return json;
                        }
                        candidate = candidate == null ? chunk.text : candidate;
                    }
                }
            }
            offset = dataEnd + 4;
            if ("IEND".equals(type)) {
                break;
            }
        }
        // Fallback: try decoding any text chunk.
        if (candidate != null) {
            return decodeCardText(candidate);
        }
        offset = PNG_SIGNATURE.length;
        while (offset + 8 <= limit) {
            String type = ascii(pngBytes, offset + 4, 4);
            int dataStart = offset + 8;
            int dataEnd = checkedDataEnd(pngBytes, offset, limit);
            if (dataEnd < 0) {
                break;
            }
            if ("tEXt".equals(type) || "iTXt".equals(type)) {
                TextChunk chunk = parseTextChunk(type, pngBytes, dataStart, dataEnd);
                if (chunk != null) {
                    String json = decodeCardText(chunk.text);
                    if (json != null) {
                        return json;
                    }
                }
            }
            offset = dataEnd + 4;
            if ("IEND".equals(type)) {
                break;
            }
        }
        return null;
    }

    private static String decodeCardText(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("{") || text.startsWith("[")) {
            return text;
        }
        if (isLikelyBase64(text)) {
            String decoded = decodeBase64(text);
            if (decoded != null) {
                String trimmed = decoded.trim();
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    return trimmed;
                }
            }
        }
        try {
            String uriDecoded = URLDecoder.decode(text, "UTF-8");
            if (uriDecoded.startsWith("{") || uriDecoded.startsWith("[")) {
                return uriDecoded;
            }
        } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
        }
        return null;
    }

    private static boolean isLikelyBase64(String text) {
        if (text.length() < 16 || text.length() % 4 != 0) {
            return false;
        }
        return text.matches("^[A-Za-z0-9+/=]+$");
    }

    private static String decodeBase64(String text) {
        try {
            byte[] decoded = Base64.decode(text, Base64.NO_WRAP);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static TextChunk parseTextChunk(String type, byte[] bytes, int start, int end) {
        if ("tEXt".equals(type)) {
            int separator = indexOfZero(bytes, start, end);
            if (separator <= start) {
                return null;
            }
            String keyword = new String(bytes, start, separator - start, StandardCharsets.ISO_8859_1);
            String text = new String(bytes, separator + 1, end - separator - 1, StandardCharsets.UTF_8);
            return new TextChunk(keyword, text);
        }
        if ("iTXt".equals(type)) {
            int cursor = start;
            int sep1 = indexOfZero(bytes, cursor, end);
            if (sep1 < 0) {
                return null;
            }
            String keyword = new String(bytes, cursor, sep1 - cursor, StandardCharsets.UTF_8);
            cursor = sep1 + 1;
            if (cursor + 1 >= end) {
                return null;
            }
            byte compressionFlag = bytes[cursor];
            cursor += 2; // flag + method
            int sep2 = indexOfZero(bytes, cursor, end);
            if (sep2 < 0) {
                return null;
            }
            cursor = sep2 + 1; // skip language tag
            int sep3 = indexOfZero(bytes, cursor, end);
            if (sep3 < 0) {
                return null;
            }
            cursor = sep3 + 1; // skip translated keyword
            if (compressionFlag != 0) {
                return null;
            }
            String text = new String(bytes, cursor, end - cursor, StandardCharsets.UTF_8);
            return new TextChunk(keyword, text);
        }
        return null;
    }

    private static int indexOfZero(byte[] bytes, int start, int end) {
        for (int index = start; index < end; index++) {
            if (bytes[index] == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int readInt32BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static int checkedDataEnd(byte[] bytes, int offset, int limit) {
        long length = Integer.toUnsignedLong(readInt32BE(bytes, offset));
        long dataEnd = offset + 8L + length;
        if (dataEnd + 4L > limit) {
            return -1;
        }
        return (int) dataEnd;
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static final class TextChunk {
        private final String keyword;
        private final String text;

        private TextChunk(String keyword, String text) {
            this.keyword = keyword;
            this.text = text;
        }
    }
}
