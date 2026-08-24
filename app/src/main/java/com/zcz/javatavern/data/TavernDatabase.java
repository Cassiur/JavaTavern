package com.zcz.javatavern.data;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.util.Log;

import java.io.File;

/**
 * Single unified SQLite database for characters, messages, presets, groups,
 * search indexes, and agent audit records.
 *
 * <p>Previously the app split data across {@code characters.db}
 * (characters + world entries) and {@code java_tavern.db} (messages + agent
 * audit + FTS). Both are now consolidated into this one {@code tavern.db} so
 * cross-entity operations are atomic. Legacy data from the two old files is
 * migrated on first open and the old files are then deleted.
 */
public final class TavernDatabase extends SQLiteOpenHelper {
    private static final String TAG = "TavernDatabase";
    private static final String DATABASE_NAME = "tavern.db";
    private static final int DATABASE_VERSION = 4;

    public static final String TABLE_CHARACTERS = "characters";
    public static final String TABLE_WORLD_ENTRIES = "world_entries";
    public static final String TABLE_MESSAGES = "messages";
    public static final String TABLE_AGENT_AUDIT = "agent_audit";
    public static final String TABLE_MESSAGES_FTS = "messages_fts";
    public static final String TABLE_PRESETS = "presets";
    public static final String TABLE_GROUPS = "groups";

    @SuppressLint("StaticFieldLeak")
    private static volatile TavernDatabase instance;
    private final Context context;

    public static TavernDatabase get(Context context) {
        TavernDatabase current = instance;
        if (current == null) {
            synchronized (TavernDatabase.class) {
                current = instance;
                if (current == null) {
                    current = new TavernDatabase(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    private TavernDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        createCharacterTables(database);
        createMessageTables(database);
        createPresetTable(database);
        createGroupTable(database);
        seedPresets(database);
        boolean migratedCharacters = migrateLegacyCharacters(database);
        migrateLegacyMessages(database);
        if (!migratedCharacters) {
            seedVera(database);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            upgradeToVersion2(database);
        }
        if (oldVersion < 3) {
            createPresetTable(database);
            seedPresets(database);
        }
        if (oldVersion < 4) {
            createGroupTable(database);
            database.execSQL("ALTER TABLE " + TABLE_MESSAGES +
                    " ADD COLUMN speaker_id TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE " + TABLE_MESSAGES +
                    " ADD COLUMN speaker_name TEXT NOT NULL DEFAULT ''");
        }
    }

    private void upgradeToVersion2(SQLiteDatabase database) {
        // 世界书高级字段：position 列已存在但旧版本存的是列表索引（语义错误），
        // 本次补充其余字段并把 position 重置为 after_char，保持既有"拼在角色卡后"行为。
        database.execSQL("ALTER TABLE " + TABLE_WORLD_ENTRIES +
                " ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 100");
        database.execSQL("ALTER TABLE " + TABLE_WORLD_ENTRIES +
                " ADD COLUMN priority INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE " + TABLE_WORLD_ENTRIES +
                " ADD COLUMN depth INTEGER NOT NULL DEFAULT 4");
        database.execSQL("ALTER TABLE " + TABLE_WORLD_ENTRIES +
                " ADD COLUMN probability INTEGER NOT NULL DEFAULT 100");
        database.execSQL("ALTER TABLE " + TABLE_WORLD_ENTRIES +
                " ADD COLUMN exclude_recursion INTEGER NOT NULL DEFAULT 0");
        database.execSQL("ALTER TABLE " + TABLE_WORLD_ENTRIES +
                " ADD COLUMN prevent_recursion INTEGER NOT NULL DEFAULT 0");
        database.execSQL("UPDATE " + TABLE_WORLD_ENTRIES + " SET position = 1");
    }

    private void createCharacterTables(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE_CHARACTERS + " (" +
                        "id TEXT PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "description TEXT NOT NULL," +
                        "greeting TEXT NOT NULL," +
                        "system_prompt TEXT NOT NULL," +
                        "accent_color INTEGER NOT NULL," +
                        "source_hash TEXT NOT NULL UNIQUE," +
                        "avatar TEXT NOT NULL DEFAULT ''," +
                        "created_at INTEGER NOT NULL)"
        );
        database.execSQL(
                "CREATE TABLE " + TABLE_WORLD_ENTRIES + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "character_id TEXT NOT NULL," +
                        "keywords_json TEXT NOT NULL," +
                        "content TEXT NOT NULL," +
                        "enabled INTEGER NOT NULL," +
                        "constant_entry INTEGER NOT NULL," +
                        "position INTEGER NOT NULL," +
                        "sort_order INTEGER NOT NULL DEFAULT 100," +
                        "priority INTEGER NOT NULL DEFAULT 0," +
                        "depth INTEGER NOT NULL DEFAULT 4," +
                        "probability INTEGER NOT NULL DEFAULT 100," +
                        "exclude_recursion INTEGER NOT NULL DEFAULT 0," +
                        "prevent_recursion INTEGER NOT NULL DEFAULT 0," +
                        "FOREIGN KEY(character_id) REFERENCES " + TABLE_CHARACTERS + "(id) ON DELETE CASCADE)"
        );
        database.execSQL(
                "CREATE INDEX index_world_entries_character ON " +
                        TABLE_WORLD_ENTRIES + "(character_id, position)"
        );
    }

    private void createMessageTables(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE_MESSAGES + " (" +
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
                        "content TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL)"
        );
        database.execSQL(
                "CREATE INDEX index_messages_character_time ON " + TABLE_MESSAGES +
                        "(character_id, created_at)"
        );
        database.execSQL(
                "CREATE TABLE " + TABLE_AGENT_AUDIT + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "character_id TEXT NOT NULL," +
                        "action_token TEXT NOT NULL," +
                        "action_type TEXT NOT NULL," +
                        "state TEXT NOT NULL," +
                        "detail TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL)"
        );
        database.execSQL(
                "CREATE INDEX index_agent_audit_character_time ON " +
                        TABLE_AGENT_AUDIT + "(character_id, created_at)"
        );
        createMessageSearch(database);
    }

    private void createMessageSearch(SQLiteDatabase database) {
        database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS " + TABLE_MESSAGES_FTS +
                        " USING fts4(content, character_id, message_id)"
        );
        database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS messages_search_insert " +
                        "AFTER INSERT ON " + TABLE_MESSAGES + " WHEN new.content != '' BEGIN " +
                        "INSERT INTO " + TABLE_MESSAGES_FTS +
                        "(content, character_id, message_id) VALUES(new.content, new.character_id, new.id); END"
        );
        database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS messages_search_delete " +
                        "AFTER DELETE ON " + TABLE_MESSAGES + " BEGIN DELETE FROM " +
                        TABLE_MESSAGES_FTS + " WHERE message_id = old.id; END"
        );
        database.execSQL(
                "CREATE TRIGGER IF NOT EXISTS messages_search_update " +
                        "AFTER UPDATE OF content ON " + TABLE_MESSAGES + " BEGIN DELETE FROM " +
                        TABLE_MESSAGES_FTS + " WHERE message_id = old.id; " +
                        "INSERT INTO " + TABLE_MESSAGES_FTS +
                        "(content, character_id, message_id) SELECT new.content, new.character_id, new.id " +
                        "WHERE new.content != ''; END"
        );
    }

    private boolean migrateLegacyCharacters(SQLiteDatabase database) {
        File legacyFile = context.getDatabasePath("characters.db");
        if (!legacyFile.exists()) {
            return false;
        }
        boolean migratedAny = false;
        boolean migrationSucceeded = false;
        database.execSQL("SAVEPOINT migrate_legacy_characters");
        try (SQLiteDatabase legacy = SQLiteDatabase.openDatabase(
                legacyFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            boolean hasAvatar = hasColumn(legacy, TABLE_CHARACTERS, "avatar");
            try (Cursor cursor = legacy.query(TABLE_CHARACTERS, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("id", cursor.getString(cursor.getColumnIndexOrThrow("id")));
                    values.put("name", cursor.getString(cursor.getColumnIndexOrThrow("name")));
                    values.put("description", cursor.getString(cursor.getColumnIndexOrThrow("description")));
                    values.put("greeting", cursor.getString(cursor.getColumnIndexOrThrow("greeting")));
                    values.put("system_prompt", cursor.getString(cursor.getColumnIndexOrThrow("system_prompt")));
                    values.put("accent_color", cursor.getInt(cursor.getColumnIndexOrThrow("accent_color")));
                    values.put("source_hash", cursor.getString(cursor.getColumnIndexOrThrow("source_hash")));
                    values.put("avatar", hasAvatar
                            ? cursor.getString(cursor.getColumnIndexOrThrow("avatar"))
                            : "");
                    values.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                    database.insertOrThrow(TABLE_CHARACTERS, null, values);
                    migratedAny = true;
                }
            }
            try (Cursor cursor = legacy.query(TABLE_WORLD_ENTRIES, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("character_id", cursor.getString(cursor.getColumnIndexOrThrow("character_id")));
                    values.put("keywords_json", cursor.getString(cursor.getColumnIndexOrThrow("keywords_json")));
                    values.put("content", cursor.getString(cursor.getColumnIndexOrThrow("content")));
                    values.put("enabled", cursor.getInt(cursor.getColumnIndexOrThrow("enabled")));
                    values.put("constant_entry", cursor.getInt(cursor.getColumnIndexOrThrow("constant_entry")));
                    values.put("position", 1);
                    database.insertOrThrow(TABLE_WORLD_ENTRIES, null, values);
                }
            }
            database.execSQL("RELEASE SAVEPOINT migrate_legacy_characters");
            migrationSucceeded = true;
        } catch (Exception exception) {
            database.execSQL("ROLLBACK TO SAVEPOINT migrate_legacy_characters");
            database.execSQL("RELEASE SAVEPOINT migrate_legacy_characters");
            Log.w(TAG, "Could not migrate legacy character database; preserving source file",
                    exception);
            throw new SQLiteException("Could not migrate legacy character database", exception);
        }
        if (migrationSucceeded && !legacyFile.delete()) {
            Log.w(TAG, "Could not delete migrated legacy character database");
        }
        return migratedAny;
    }

    private void migrateLegacyMessages(SQLiteDatabase database) {
        File legacyFile = context.getDatabasePath("java_tavern.db");
        if (!legacyFile.exists()) {
            return;
        }
        boolean migrationSucceeded = false;
        database.execSQL("SAVEPOINT migrate_legacy_messages");
        try (SQLiteDatabase legacy = SQLiteDatabase.openDatabase(
                legacyFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY)) {
            try (Cursor cursor = legacy.query(TABLE_MESSAGES, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("character_id", cursor.getString(cursor.getColumnIndexOrThrow("character_id")));
                    values.put("role", cursor.getString(cursor.getColumnIndexOrThrow("role")));
                    values.put("kind", cursor.getString(cursor.getColumnIndexOrThrow("kind")));
                    values.put("title", cursor.getString(cursor.getColumnIndexOrThrow("title")));
                    values.put("action_token", cursor.getString(cursor.getColumnIndexOrThrow("action_token")));
                    values.put("action_type", cursor.getString(cursor.getColumnIndexOrThrow("action_type")));
                    values.put("action_state", cursor.getString(cursor.getColumnIndexOrThrow("action_state")));
                    values.put("attachment_path", cursor.getString(cursor.getColumnIndexOrThrow("attachment_path")));
                    values.put("attachment_mime_type", cursor.getString(cursor.getColumnIndexOrThrow("attachment_mime_type")));
                    values.put("reply_to_message_id", cursor.getLong(cursor.getColumnIndexOrThrow("reply_to_message_id")));
                    values.put("reply_preview", cursor.getString(cursor.getColumnIndexOrThrow("reply_preview")));
                    values.put("reaction", cursor.getString(cursor.getColumnIndexOrThrow("reaction")));
                    values.put("content", cursor.getString(cursor.getColumnIndexOrThrow("content")));
                    values.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                    database.insertOrThrow(TABLE_MESSAGES, null, values);
                }
            }
            try (Cursor cursor = legacy.query(TABLE_AGENT_AUDIT, null, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("character_id", cursor.getString(cursor.getColumnIndexOrThrow("character_id")));
                    values.put("action_token", cursor.getString(cursor.getColumnIndexOrThrow("action_token")));
                    values.put("action_type", cursor.getString(cursor.getColumnIndexOrThrow("action_type")));
                    values.put("state", cursor.getString(cursor.getColumnIndexOrThrow("state")));
                    values.put("detail", cursor.getString(cursor.getColumnIndexOrThrow("detail")));
                    values.put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                    database.insertOrThrow(TABLE_AGENT_AUDIT, null, values);
                }
            }
            database.execSQL("RELEASE SAVEPOINT migrate_legacy_messages");
            migrationSucceeded = true;
        } catch (Exception exception) {
            database.execSQL("ROLLBACK TO SAVEPOINT migrate_legacy_messages");
            database.execSQL("RELEASE SAVEPOINT migrate_legacy_messages");
            Log.w(TAG, "Could not migrate legacy message database; preserving source file",
                    exception);
            throw new SQLiteException("Could not migrate legacy message database", exception);
        }
        if (migrationSucceeded && !legacyFile.delete()) {
            Log.w(TAG, "Could not delete migrated legacy message database");
        }
    }

    private boolean hasColumn(SQLiteDatabase database, String table, String column) {
        try (Cursor cursor = database.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void createPresetTable(SQLiteDatabase database) {
        // 采样参数统一用 TEXT 存储（null=不发送），避免 float 精度污染，与
        // SecureModelSettingsStore 的字符串存储保持一致。
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_PRESETS + " (" +
                        "id TEXT PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "temperature TEXT," +
                        "top_p TEXT," +
                        "max_tokens TEXT," +
                        "frequency_penalty TEXT," +
                        "presence_penalty TEXT," +
                        "created_at INTEGER NOT NULL)"
        );
    }

    private void createGroupTable(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS " + TABLE_GROUPS + " (" +
                        "id TEXT PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "member_ids_json TEXT NOT NULL," +
                        "created_at INTEGER NOT NULL)"
        );
    }

    private void seedPresets(SQLiteDatabase database) {
        for (GenerationPreset preset : GenerationPreset.builtInPresets()) {
            ContentValues values = new ContentValues();
            values.put("id", preset.getId());
            values.put("name", preset.getName());
            values.put("created_at", 0L);
            putParam(values, "temperature", preset.getParams().getTemperature());
            putParam(values, "top_p", preset.getParams().getTopP());
            putParam(values, "max_tokens", preset.getParams().getMaxTokens());
            putParam(values, "frequency_penalty", preset.getParams().getFrequencyPenalty());
            putParam(values, "presence_penalty", preset.getParams().getPresencePenalty());
            database.insertWithOnConflict(TABLE_PRESETS, null, values,
                    SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private void putParam(ContentValues values, String key, Number value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, String.valueOf(value));
        }
    }

    private void seedVera(SQLiteDatabase database) {
        ContentValues values = new ContentValues();
        values.put("id", "innkeeper-vera");
        values.put("name", "薇拉");
        values.put("description", "深夜酒馆的老板娘。银壶温酒，等南来北往的旅人讲完他们的故事，再递上一杯热饮。见过太多离别后，反而更珍惜每一次重逢的烛光。");
        values.put("greeting", "（擦着吧台，抬头看见你）今晚风大。先坐下，我给你温一壶热茶——然后讲讲你来的路上，遇到了什么人？");
        values.put("system_prompt", "用叙述者的口吻，扮演「深夜酒馆的老板娘薇拉」。说话温和、有耐心，会主动倾听并用具体的小细节回应（酒的温度、窗外的雨、烛火的方向）。从不催促对方，遇到对方沉默时也不强行追问，而是端上一杯热饮、轻轻说一句「我在这里」。有自己藏着的往事，但不主动透露；只在对方问起、且气氛合适时，才低声讲一小段自己过去的事。");
        values.put("accent_color", Color.rgb(176, 96, 64));
        values.put("source_hash", "builtin:innkeeper-vera");
        values.put("avatar", "res:avatar_vera");
        values.put("created_at", 0);
        database.insertOrThrow(TABLE_CHARACTERS, null, values);
    }
}
