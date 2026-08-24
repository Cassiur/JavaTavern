package com.zcz.javatavern.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class PngCharacterCardReaderTest {
    private static final byte[] SIGNATURE = {
            (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G',
            0x0D, 0x0A, 0x1A, 0x0A
    };

    @Test
    public void rawJsonTextChunk_isExtracted() throws IOException {
        String json = "{\"name\":\"Vera\"}";

        byte[] png = pngWithTextChunk("chara", json);

        assertTrue(PngCharacterCardReader.isPng(png));
        assertEquals(json, PngCharacterCardReader.extractCardJson(png));
    }

    @Test
    public void oversizedUnsignedChunkLength_isRejected() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(SIGNATURE);
        output.write(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        output.write("tEXt".getBytes(StandardCharsets.US_ASCII));
        output.write(new byte[4]);

        assertNull(PngCharacterCardReader.extractCardJson(output.toByteArray()));
    }

    @Test
    public void truncatedChunk_isRejected() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(SIGNATURE);
        writeInt(output, 32);
        output.write("tEXt".getBytes(StandardCharsets.US_ASCII));
        output.write(new byte[]{1, 2, 3});

        assertNull(PngCharacterCardReader.extractCardJson(output.toByteArray()));
    }

    private static byte[] pngWithTextChunk(String keyword, String text) throws IOException {
        byte[] data = (keyword + '\0' + text).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(SIGNATURE);
        writeInt(output, data.length);
        output.write("tEXt".getBytes(StandardCharsets.US_ASCII));
        output.write(data);
        output.write(new byte[4]);
        return output.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }
}
