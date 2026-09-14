package com.zcz.javatavern.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Exercises the full export -&gt; restore round trip of {@link
 * BackupRepository} against Robolectric's real SQLite and filesystem.
 *
 * <p>{@code docs/CHANGELOG.md} claims this round trip was "verified by a
 * scripted full round-trip" — that was a manual script, not an automated
 * test. This turns the same invariants (table fidelity, path rewriting,
 * orphan pruning, full-replace-not-merge semantics) into a regression test.
 */
@RunWith(RobolectricTestRunner.class)
public final class BackupRepositoryTest {
    private Context context;
    private BackupRepository repository;
    private TavernDatabase database;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        repository = new BackupRepository(context);
        database = TavernDatabase.get(context);
        // Fresh installs seed a built-in character; clear it so these tests
        // reason about known, fully-controlled row counts.
        database.getWritableDatabase().delete(TavernDatabase.TABLE_CHARACTERS, null, null);
    }

    @After
    public void tearDown() {
        TavernDatabase.resetSingletonForTest();
    }

    @Test
    public void restore_replacesExistingData_rewritesFilePaths_andPrunesOrphans()
            throws IOException, BackupException {
        String avatarPath = writeManagedFile("avatars/vera.png", "AVATAR-BYTES");
        String attachmentPath = writeManagedFile("message_images/pic.jpg", "IMAGE-BYTES");
        insertCharacter("vera", "Vera", avatarPath);
        insertMessage("vera", "hello with a picture", attachmentPath);

        Uri backupUri = Uri.fromFile(new File(context.getCacheDir(), "backup.zip"));
        BackupRepository.Summary exportSummary = repository.export(backupUri);
        assertEquals(1, exportSummary.getCharacterCount());
        assertEquals(1, exportSummary.getMessageCount());

        // Data drifts after the export was taken: a second character with its
        // own avatar is added but never makes it into the backup.
        String orphanAvatarPath = writeManagedFile("avatars/orphan.png", "ORPHAN-BYTES");
        insertCharacter("extra", "Extra", orphanAvatarPath);
        assertEquals(2, countRows(TavernDatabase.TABLE_CHARACTERS));

        BackupRepository.Summary restoreSummary = repository.restore(backupUri);

        assertEquals("restore replaces the table, it does not merge into it",
                1, countRows(TavernDatabase.TABLE_CHARACTERS));
        assertEquals(1, restoreSummary.getCharacterCount());
        assertEquals(1, restoreSummary.getMessageCount());

        try (android.database.Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_CHARACTERS,
                new String[]{"name", "avatar"},
                "id = ?", new String[]{"vera"}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Vera", cursor.getString(0));
            String restoredAvatarPath = cursor.getString(1);
            assertEquals("same-device restore rewrites back to the identical absolute path",
                    avatarPath, restoredAvatarPath);
            assertEquals("AVATAR-BYTES", readFile(restoredAvatarPath));
        }

        try (android.database.Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_MESSAGES,
                new String[]{"content", "attachment_path"},
                "character_id = ?", new String[]{"vera"}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("hello with a picture", cursor.getString(0));
            assertEquals("IMAGE-BYTES", readFile(cursor.getString(1)));
        }

        assertFalse("the extra character's avatar is no longer referenced and must be pruned",
                new File(orphanAvatarPath).exists());
        assertTrue("the restored character's own avatar must survive pruning",
                new File(avatarPath).exists());
    }

    @Test
    public void restore_rejectsArchiveMissingManifest() throws IOException {
        File badArchive = new File(context.getCacheDir(), "not-a-backup.zip");
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                new FileOutputStream(badArchive))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("tables.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        insertCharacter("keep-me", "Untouched", "");

        try {
            repository.restore(Uri.fromFile(badArchive));
            org.junit.Assert.fail("an archive without manifest.json must be rejected");
        } catch (BackupException expected) {
            // expected: not a JavaTavern backup
        }

        assertEquals("a rejected archive must not touch existing data",
                1, countRows(TavernDatabase.TABLE_CHARACTERS));
    }

    /**
     * Writes the file at {@code filesDir + "/" + relativePath}, joined with a
     * forward slash exactly like {@link BackupPaths} assumes real Android
     * device paths look ({@code File}'s own path-joining constructor would
     * normalize to backslashes when this test runs on a Windows host,
     * breaking {@code BackupPaths}' prefix matching even though real devices
     * are never affected — Android filesystem paths are always "/"-joined).
     */
    private String writeManagedFile(String relativePath, String content) throws IOException {
        String absolutePath = context.getFilesDir().getAbsolutePath() + "/" + relativePath;
        File file = new File(absolutePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertTrue(parent.mkdirs());
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return absolutePath;
    }

    private String readFile(String absolutePath) throws IOException {
        return new String(
                java.nio.file.Files.readAllBytes(new File(absolutePath).toPath()),
                StandardCharsets.UTF_8);
    }

    private void insertCharacter(String id, String name, String avatarPath) {
        SQLiteDatabase writable = database.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("description", "");
        values.put("greeting", "");
        values.put("system_prompt", "");
        values.put("accent_color", 0);
        values.put("source_hash", "hash-" + id);
        values.put("avatar", avatarPath);
        values.put("created_at", 0L);
        writable.insertOrThrow(TavernDatabase.TABLE_CHARACTERS, null, values);
    }

    private void insertMessage(String characterId, String content, String attachmentPath) {
        SQLiteDatabase writable = database.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("character_id", characterId);
        values.put("role", "ASSISTANT");
        values.put("content", content);
        values.put("created_at", 1000L);
        values.put("attachment_path", attachmentPath);
        values.put("attachment_mime_type", "image/jpeg");
        writable.insertOrThrow(TavernDatabase.TABLE_MESSAGES, null, values);
    }

    private int countRows(String table) {
        try (android.database.Cursor cursor = database.getReadableDatabase()
                .rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
}
