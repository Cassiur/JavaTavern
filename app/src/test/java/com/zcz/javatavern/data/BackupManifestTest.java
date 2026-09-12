package com.zcz.javatavern.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 备份清单的读写与版本校验：错的归档必须在动数据之前就被挡下来。 */
public class BackupManifestTest {

    private static BackupManifest sample() throws Exception {
        JSONObject settings = new JSONObject();
        settings.put("baseUrl", "https://api.openai.com/v1");
        settings.put("model", "gpt-4o-mini");
        settings.put("temperature", 0.8);

        JSONObject memories = new JSONObject();
        memories.put("innkeeper-vera", new JSONArray().put(new JSONObject()
                .put("id", "m1")
                .put("content", "她记得我上次点了热茶")
                .put("createdAt", 1000L)));

        JSONObject drafts = new JSONObject();
        drafts.put("innkeeper-vera", "还没发出去的一句话");

        return new BackupManifest(
                1_700_000_000_000L,
                "0.4.0",
                5,
                5,
                java.util.Map.of("characters", 2, "messages", 40),
                java.util.List.of("avatars/a.png", "message_images/b.jpg"),
                settings,
                memories,
                drafts
        );
    }

    @Test
    public void roundTripKeepsMetadata() throws Exception {
        JSONObject json = sample().toJson();
        BackupManifest parsed = BackupManifest.fromJson(json);

        assertEquals(BackupManifest.FORMAT_VERSION, json.getInt("formatVersion"));
        assertEquals(BackupManifest.FORMAT, json.getString("format"));
        assertEquals(1_700_000_000_000L, parsed.getExportedAt());
        assertEquals("0.4.0", parsed.getAppVersionName());
        assertEquals(5, parsed.getAppVersionCode());
        assertEquals(5, parsed.getDatabaseVersion());
        assertEquals(2, parsed.countOf("characters"));
        assertEquals(40, parsed.countOf("messages"));
        assertEquals(0, parsed.countOf("groups"));
        assertEquals(2, parsed.getFiles().size());
        assertEquals("还没发出去的一句话",
                parsed.getDrafts().optString("innkeeper-vera"));
        assertEquals(0.8, parsed.getSettings().optDouble("temperature"), 0.0001);
    }

    @Test
    public void apiKeyIsNeverPartOfTheManifest() throws Exception {
        JSONObject settings = new JSONObject().put("baseUrl", "https://example.com/v1");
        BackupManifest manifest = new BackupManifest(
                1L, "0.4.0", 5, 5, java.util.Map.of(), java.util.List.of(),
                settings, new JSONObject(), new JSONObject());

        String serialized = manifest.toJson().toString();
        assertFalse(serialized.contains("apiKey"));
        assertFalse(serialized.contains("api_key"));
        assertFalse(serialized.contains("sk-"));
    }

    @Test
    public void foreignFileIsRejected() throws Exception {
        JSONObject json = new JSONObject().put("format", "something.else");
        BackupException error = assertThrows(
                BackupException.class, () -> BackupManifest.fromJson(json));
        assertTrue(error.getMessage().contains("JavaTavern"));
    }

    @Test
    public void newerFormatVersionIsRejectedWithActionableMessage() throws Exception {
        JSONObject json = sample().toJson();
        json.put("formatVersion", BackupManifest.FORMAT_VERSION + 1);

        BackupException error = assertThrows(
                BackupException.class, () -> BackupManifest.fromJson(json));
        assertTrue(error.getMessage().contains("升级应用"));
    }

    @Test
    public void emptyDocumentIsRejected() throws Exception {
        assertThrows(BackupException.class, () -> BackupManifest.fromJson(null));
        assertThrows(BackupException.class, () -> BackupManifest.fromJson(new JSONObject()));
    }

    @Test
    public void unmanagedFileEntriesAreDroppedOnParse() throws Exception {
        JSONObject json = sample().toJson();
        json.put("files", new JSONArray()
                .put("avatars/a.png")
                .put("../../shared_prefs/x.xml")
                .put("/etc/passwd")
                .put("databases/tavern.db"));

        BackupManifest parsed = BackupManifest.fromJson(json);

        assertEquals(1, parsed.getFiles().size());
        assertEquals("avatars/a.png", parsed.getFiles().get(0));
    }

    @Test
    public void missingOptionalSectionsStillParse() throws Exception {
        JSONObject json = new JSONObject()
                .put("format", BackupManifest.FORMAT)
                .put("formatVersion", BackupManifest.FORMAT_VERSION)
                .put("exportedAt", 42L);

        BackupManifest parsed = BackupManifest.fromJson(json);

        assertEquals(42L, parsed.getExportedAt());
        assertEquals(0, parsed.getFiles().size());
        assertEquals(0, parsed.getMemories().length());
        assertEquals(0, parsed.getSettings().length());
    }
}
