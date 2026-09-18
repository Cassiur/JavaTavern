package com.zcz.javatavern.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;

/**
 * Exercises {@link TavernDatabase} against Robolectric's real (JVM-local)
 * SQLite implementation — no emulator required. These tests specifically
 * target claims made in {@code docs/CODE_REVIEW.md} that had no automated
 * coverage before this round: that the v1-&gt;v5 schema upgrade preserves
 * existing data, and that a failed legacy-database migration rolls back and
 * preserves the source file instead of silently losing data.
 */
@RunWith(RobolectricTestRunner.class)
public final class TavernDatabaseTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        TavernDatabase.resetSingletonForTest();
    }

    @Test
    public void freshInstall_createsFullSchemaAndSeedsDefaults() {
        SQLiteDatabase database = TavernDatabase.get(context).getWritableDatabase();

        assertTrue(tableExists(database, TavernDatabase.TABLE_CHARACTERS));
        assertTrue(tableExists(database, TavernDatabase.TABLE_WORLD_ENTRIES));
        assertTrue(tableExists(database, TavernDatabase.TABLE_MESSAGES));
        assertTrue(tableExists(database, TavernDatabase.TABLE_MESSAGE_VERSIONS));
        assertTrue(tableExists(database, TavernDatabase.TABLE_AGENT_AUDIT));
        assertTrue(tableExists(database, TavernDatabase.TABLE_PRESETS));
        assertTrue(tableExists(database, TavernDatabase.TABLE_GROUPS));
        assertTrue(tableExists(database, TavernDatabase.TABLE_PERSONAS));
        assertTrue(tableExists(database, TavernDatabase.TABLE_CHATS));
        assertTrue("fresh installs must have messages.chat_id from onCreate, " +
                        "not only via upgradeToVersion8",
                columnExists(database, TavernDatabase.TABLE_MESSAGES, "chat_id"));

        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + TavernDatabase.TABLE_CHARACTERS, null)) {
            cursor.moveToFirst();
            assertEquals("fresh install seeds the built-in innkeeper character",
                    1, cursor.getInt(0));
        }
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + TavernDatabase.TABLE_PRESETS, null)) {
            cursor.moveToFirst();
            assertTrue("fresh install seeds built-in generation presets",
                    cursor.getInt(0) > 0);
        }
    }

    /**
     * Builds the schema exactly as it existed at {@code DATABASE_VERSION == 1}
     * (before {@code upgradeToVersion2}/3/4/5 ran), seeds one row per table,
     * then opens it through {@link TavernDatabase} so the real {@code
     * onUpgrade} chain executes. This is the migration path every real
     * installed user goes through on update; it previously had zero
     * automated coverage.
     */
    @Test
    public void upgradeFromVersion1_addsNewColumnsAndPreservesExistingData() {
        File dbFile = context.getDatabasePath("tavern.db");
        try (SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacy.execSQL("CREATE TABLE characters (" +
                    "id TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "description TEXT NOT NULL," +
                    "greeting TEXT NOT NULL," +
                    "system_prompt TEXT NOT NULL," +
                    "accent_color INTEGER NOT NULL," +
                    "source_hash TEXT NOT NULL UNIQUE," +
                    "avatar TEXT NOT NULL DEFAULT ''," +
                    "created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE world_entries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "character_id TEXT NOT NULL," +
                    "keywords_json TEXT NOT NULL," +
                    "content TEXT NOT NULL," +
                    "enabled INTEGER NOT NULL," +
                    "constant_entry INTEGER NOT NULL," +
                    "position INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "character_id TEXT NOT NULL," +
                    "role TEXT NOT NULL," +
                    "kind TEXT NOT NULL DEFAULT 'TEXT'," +
                    "title TEXT NOT NULL DEFAULT ''," +
                    "action_token TEXT NOT NULL DEFAULT ''," +
                    "action_type TEXT NOT NULL DEFAULT ''," +
                    "action_state TEXT NOT NULL DEFAULT 'NONE'," +
                    "attachment_path TEXT NOT NULL DEFAULT ''," +
                    "attachment_mime_type TEXT NOT NULL DEFAULT ''," +
                    "reply_to_message_id INTEGER NOT NULL DEFAULT -1," +
                    "reply_preview TEXT NOT NULL DEFAULT ''," +
                    "reaction TEXT NOT NULL DEFAULT ''," +
                    "content TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE agent_audit (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "character_id TEXT NOT NULL," +
                    "action_token TEXT NOT NULL," +
                    "action_type TEXT NOT NULL," +
                    "state TEXT NOT NULL," +
                    "detail TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL)");

            ContentValues character = new ContentValues();
            character.put("id", "pre-existing-character");
            character.put("name", "Old Data");
            character.put("description", "must survive the upgrade");
            character.put("greeting", "hi");
            character.put("system_prompt", "be nice");
            character.put("accent_color", 0);
            character.put("source_hash", "hash-1");
            character.put("avatar", "");
            character.put("created_at", 1000L);
            legacy.insertOrThrow("characters", null, character);

            ContentValues worldEntry = new ContentValues();
            worldEntry.put("character_id", "pre-existing-character");
            worldEntry.put("keywords_json", "[\"old\"]");
            worldEntry.put("content", "world entry content must survive");
            worldEntry.put("enabled", 1);
            worldEntry.put("constant_entry", 0);
            worldEntry.put("position", 0);
            legacy.insertOrThrow("world_entries", null, worldEntry);

            ContentValues message = new ContentValues();
            message.put("character_id", "pre-existing-character");
            message.put("role", "USER");
            message.put("content", "message content must survive");
            message.put("created_at", 2000L);
            legacy.insertOrThrow("messages", null, message);

            legacy.execSQL("PRAGMA user_version = 1");
        }

        // Reopening through TavernDatabase triggers onUpgrade(db, 1, 5).
        SQLiteDatabase upgraded = TavernDatabase.get(context).getWritableDatabase();

        try (Cursor cursor = upgraded.query(
                TavernDatabase.TABLE_WORLD_ENTRIES,
                new String[]{"content", "position", "sort_order", "priority", "depth",
                        "probability", "exclude_recursion", "prevent_recursion"},
                "character_id = ?",
                new String[]{"pre-existing-character"},
                null, null, null)) {
            assertTrue("pre-existing world entry row must survive the upgrade",
                    cursor.moveToFirst());
            assertEquals("world entry content must survive", cursor.getString(0));
            assertEquals("upgradeToVersion2 resets position to after_char (1)",
                    1, cursor.getInt(1));
            assertEquals(100, cursor.getInt(2));
            assertEquals(0, cursor.getInt(3));
            assertEquals(4, cursor.getInt(4));
            assertEquals(100, cursor.getInt(5));
            assertEquals(0, cursor.getInt(6));
            assertEquals(0, cursor.getInt(7));
        }

        try (Cursor cursor = upgraded.query(
                TavernDatabase.TABLE_MESSAGES,
                new String[]{"content", "speaker_id", "speaker_name", "version_count",
                        "active_version"},
                "character_id = ?",
                new String[]{"pre-existing-character"},
                null, null, null)) {
            assertTrue("pre-existing message row must survive the upgrade",
                    cursor.moveToFirst());
            assertEquals("message content must survive", cursor.getString(0));
            assertEquals("", cursor.getString(1));
            assertEquals("", cursor.getString(2));
            assertEquals(1, cursor.getInt(3));
            assertEquals(1, cursor.getInt(4));
        }

        try (Cursor cursor = upgraded.query(
                TavernDatabase.TABLE_CHARACTERS,
                new String[]{"name"},
                "id = ?",
                new String[]{"pre-existing-character"},
                null, null, null)) {
            assertTrue("pre-existing character row must survive the upgrade",
                    cursor.moveToFirst());
            assertEquals("Old Data", cursor.getString(0));
        }

        assertTrue("v3 preset table must exist and be seeded after upgrade",
                tableExists(upgraded, TavernDatabase.TABLE_PRESETS));
        try (Cursor cursor = upgraded.rawQuery(
                "SELECT COUNT(*) FROM " + TavernDatabase.TABLE_PRESETS, null)) {
            cursor.moveToFirst();
            assertTrue(cursor.getInt(0) > 0);
        }
        assertTrue("v4 group table must exist after upgrade",
                tableExists(upgraded, TavernDatabase.TABLE_GROUPS));
        assertTrue("v5 message_versions table must exist after upgrade",
                tableExists(upgraded, TavernDatabase.TABLE_MESSAGE_VERSIONS));
    }

    /**
     * Regression test for a real bug: {@code upgradeToVersion8} (adds
     * {@code messages.chat_id} and the {@code chats} table) was defined but
     * never wired into {@code onUpgrade}, so real users upgrading from v7
     * would hit {@code SQLiteException: no such column: chat_id} the first
     * time any multi-chat code ran a query against it. Builds the schema as
     * it existed at v7 (after upgradeToVersion6/7, before upgradeToVersion8),
     * seeds a pre-existing message with no chat_id, then reopens through
     * {@link TavernDatabase} so the real {@code onUpgrade(db, 7, 8)} runs.
     */
    @Test
    public void upgradeFromVersion7_addsChatIdAndMigratesExistingMessagesToDefaultChat() {
        File dbFile = context.getDatabasePath("tavern.db");
        try (SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(dbFile, null)) {
            legacy.execSQL("CREATE TABLE characters (" +
                    "id TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "description TEXT NOT NULL," +
                    "personality TEXT NOT NULL DEFAULT ''," +
                    "scenario TEXT NOT NULL DEFAULT ''," +
                    "greeting TEXT NOT NULL," +
                    "system_prompt TEXT NOT NULL," +
                    "post_history_instructions TEXT NOT NULL DEFAULT ''," +
                    "creator_notes TEXT NOT NULL DEFAULT ''," +
                    "character_version TEXT NOT NULL DEFAULT ''," +
                    "mes_example TEXT NOT NULL DEFAULT ''," +
                    "alternate_greetings_json TEXT NOT NULL DEFAULT '[]'," +
                    "accent_color INTEGER NOT NULL," +
                    "source_hash TEXT NOT NULL UNIQUE," +
                    "avatar TEXT NOT NULL DEFAULT ''," +
                    "created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE world_entries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "character_id TEXT NOT NULL," +
                    "keywords_json TEXT NOT NULL," +
                    "secondary_keys_json TEXT NOT NULL DEFAULT '[]'," +
                    "content TEXT NOT NULL," +
                    "enabled INTEGER NOT NULL," +
                    "constant_entry INTEGER NOT NULL," +
                    "position INTEGER NOT NULL," +
                    "sort_order INTEGER NOT NULL DEFAULT 100," +
                    "priority INTEGER NOT NULL DEFAULT 0," +
                    "depth INTEGER NOT NULL DEFAULT 4," +
                    "scan_depth INTEGER NOT NULL DEFAULT 100," +
                    "case_sensitive INTEGER NOT NULL DEFAULT 0," +
                    "match_whole_words INTEGER NOT NULL DEFAULT 0," +
                    "use_group_scoring INTEGER NOT NULL DEFAULT 0," +
                    "automation_id TEXT NOT NULL DEFAULT ''," +
                    "role TEXT NOT NULL DEFAULT 'system'," +
                    "vectorized INTEGER NOT NULL DEFAULT 0," +
                    "sticky INTEGER NOT NULL DEFAULT 0," +
                    "cooldown INTEGER NOT NULL DEFAULT 0," +
                    "probability INTEGER NOT NULL DEFAULT 100," +
                    "exclude_recursion INTEGER NOT NULL DEFAULT 0," +
                    "prevent_recursion INTEGER NOT NULL DEFAULT 0)");
            legacy.execSQL("CREATE TABLE messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "character_id TEXT NOT NULL," +
                    "role TEXT NOT NULL," +
                    "kind TEXT NOT NULL DEFAULT 'TEXT'," +
                    "title TEXT NOT NULL DEFAULT ''," +
                    "action_token TEXT NOT NULL DEFAULT ''," +
                    "action_type TEXT NOT NULL DEFAULT ''," +
                    "action_state TEXT NOT NULL DEFAULT 'NONE'," +
                    "attachment_path TEXT NOT NULL DEFAULT ''," +
                    "attachment_mime_type TEXT NOT NULL DEFAULT ''," +
                    "reply_to_message_id INTEGER NOT NULL DEFAULT -1," +
                    "reply_preview TEXT NOT NULL DEFAULT ''," +
                    "reaction TEXT NOT NULL DEFAULT ''," +
                    "speaker_id TEXT NOT NULL DEFAULT ''," +
                    "speaker_name TEXT NOT NULL DEFAULT ''," +
                    "version_count INTEGER NOT NULL DEFAULT 1," +
                    "active_version INTEGER NOT NULL DEFAULT 1," +
                    "content TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE agent_audit (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "character_id TEXT NOT NULL," +
                    "action_token TEXT NOT NULL," +
                    "action_type TEXT NOT NULL," +
                    "state TEXT NOT NULL," +
                    "detail TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE presets (" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, temperature TEXT, top_p TEXT," +
                    "max_tokens TEXT, frequency_penalty TEXT, presence_penalty TEXT," +
                    "created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE groups (" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, member_ids_json TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE message_versions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, message_id INTEGER NOT NULL," +
                    "content TEXT NOT NULL, created_at INTEGER NOT NULL)");
            legacy.execSQL("CREATE TABLE personas (" +
                    "id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT ''," +
                    "is_default INTEGER NOT NULL DEFAULT 0, avatar TEXT NOT NULL DEFAULT ''," +
                    "created_at INTEGER NOT NULL)");

            ContentValues character = new ContentValues();
            character.put("id", "pre-v8-character");
            character.put("name", "Old Data");
            character.put("description", "must survive the v8 upgrade");
            character.put("greeting", "hi");
            character.put("system_prompt", "be nice");
            character.put("accent_color", 0);
            character.put("source_hash", "hash-v7");
            character.put("avatar", "");
            character.put("created_at", 1000L);
            legacy.insertOrThrow("characters", null, character);

            ContentValues message = new ContentValues();
            message.put("character_id", "pre-v8-character");
            message.put("role", "USER");
            message.put("content", "message from before multi-chat existed");
            message.put("created_at", 2000L);
            legacy.insertOrThrow("messages", null, message);

            legacy.execSQL("PRAGMA user_version = 7");
        }

        // Reopening through TavernDatabase triggers onUpgrade(db, 7, 8).
        SQLiteDatabase upgraded = TavernDatabase.get(context).getWritableDatabase();

        assertTrue("v8 chats table must exist after upgrade",
                tableExists(upgraded, TavernDatabase.TABLE_CHATS));
        assertTrue("v8 messages.chat_id column must exist after upgrade",
                columnExists(upgraded, TavernDatabase.TABLE_MESSAGES, "chat_id"));

        String expectedChatId = "default-pre-v8-character";
        try (Cursor cursor = upgraded.query(
                TavernDatabase.TABLE_CHATS,
                new String[]{"character_id", "name"},
                "id = ?",
                new String[]{expectedChatId},
                null, null, null)) {
            assertTrue("a default chat must be synthesized for the pre-existing character",
                    cursor.moveToFirst());
            assertEquals("pre-v8-character", cursor.getString(0));
            assertEquals("默认聊天", cursor.getString(1));
        }

        try (Cursor cursor = upgraded.query(
                TavernDatabase.TABLE_MESSAGES,
                new String[]{"content", "chat_id"},
                "character_id = ?",
                new String[]{"pre-v8-character"},
                null, null, null)) {
            assertTrue("pre-existing message row must survive the upgrade",
                    cursor.moveToFirst());
            assertEquals("message from before multi-chat existed", cursor.getString(0));
            assertEquals("pre-existing message must be bucketed into the default chat",
                    expectedChatId, cursor.getString(1));
        }
    }

    @Test
    public void legacyCharacterMigration_movesRowsAndDeletesSourceFile() {
        File legacyFile = context.getDatabasePath("characters.db");
        try (SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)) {
            legacy.execSQL("CREATE TABLE characters (" +
                    "id TEXT PRIMARY KEY, name TEXT, description TEXT, greeting TEXT," +
                    "system_prompt TEXT, accent_color INTEGER, source_hash TEXT," +
                    "avatar TEXT, created_at INTEGER)");
            legacy.execSQL("CREATE TABLE world_entries (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, character_id TEXT," +
                    "keywords_json TEXT, content TEXT, enabled INTEGER, constant_entry INTEGER," +
                    "position INTEGER)");
            ContentValues character = new ContentValues();
            character.put("id", "legacy-char");
            character.put("name", "Legacy");
            character.put("description", "from the split-database era");
            character.put("greeting", "hi");
            character.put("system_prompt", "be nice");
            character.put("accent_color", 0);
            character.put("source_hash", "legacy-hash");
            character.put("avatar", "res:legacy");
            character.put("created_at", 500L);
            legacy.insertOrThrow("characters", null, character);
        }
        assertTrue("legacy source file must exist before migration", legacyFile.exists());

        SQLiteDatabase merged = TavernDatabase.get(context).getWritableDatabase();

        try (Cursor cursor = merged.query(
                TavernDatabase.TABLE_CHARACTERS,
                new String[]{"name", "avatar"},
                "id = ?",
                new String[]{"legacy-char"},
                null, null, null)) {
            assertTrue("migrated character row must be present in the unified database",
                    cursor.moveToFirst());
            assertEquals("Legacy", cursor.getString(0));
            assertEquals("res:legacy", cursor.getString(1));
        }
        assertTrue("successful migration deletes the legacy source file",
                !legacyFile.exists());
    }

    /**
     * A legacy database missing an expected column (simulating a corrupt or
     * unexpected file) must make {@code migrateLegacyCharacters} roll back
     * and preserve the source file rather than partially import data — this
     * is the "失败保留旧库" contract documented across the project's review
     * notes but never previously exercised by a test.
     */
    @Test
    public void legacyCharacterMigration_onFailure_rollsBackAndPreservesSourceFile() {
        File legacyFile = context.getDatabasePath("characters.db");
        try (SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(legacyFile, null)) {
            // Missing every column migrateLegacyCharacters() expects to read,
            // so the migration must fail before any row is copied.
            legacy.execSQL("CREATE TABLE characters (id TEXT PRIMARY KEY)");
            legacy.execSQL("CREATE TABLE world_entries (id INTEGER PRIMARY KEY)");
            ContentValues row = new ContentValues();
            row.put("id", "broken-char");
            legacy.insertOrThrow("characters", null, row);
        }

        try {
            TavernDatabase.get(context).getWritableDatabase();
            fail("a malformed legacy database must abort the migration, not silently succeed");
        } catch (SQLiteException expected) {
            // TavernDatabase wraps the underlying column-not-found failure.
        }

        assertTrue("failed migration must preserve the legacy source file",
                legacyFile.exists());

        try (SQLiteDatabase reopened = SQLiteDatabase.openDatabase(
                legacyFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            try (Cursor cursor = reopened.rawQuery(
                    "SELECT COUNT(*) FROM characters", null)) {
                cursor.moveToFirst();
                assertEquals("the source row must be untouched after rollback",
                        1, cursor.getInt(0));
            }
        }
    }

    private boolean tableExists(SQLiteDatabase database, String tableName) {
        try (Cursor cursor = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{tableName})) {
            return cursor.moveToFirst();
        }
    }

    private boolean columnExists(SQLiteDatabase database, String table, String column) {
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }
}
