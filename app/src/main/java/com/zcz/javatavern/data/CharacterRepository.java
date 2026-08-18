package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;

import com.zcz.javatavern.model.CharacterCardData;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.WorldBookEntry;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CharacterRepository extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "characters.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_CHARACTERS = "characters";
    private static final String TABLE_WORLD_ENTRIES = "world_entries";

    public CharacterRepository(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE_CHARACTERS + " (" +
                        "id TEXT PRIMARY KEY," +
                        "name TEXT NOT NULL," +
                        "description TEXT NOT NULL," +
                        "greeting TEXT NOT NULL," +
                        "system_prompt TEXT NOT NULL," +
                        "accent_color INTEGER NOT NULL," +
                        "source_hash TEXT NOT NULL UNIQUE," +
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
                        "FOREIGN KEY(character_id) REFERENCES " + TABLE_CHARACTERS + "(id) ON DELETE CASCADE)"
        );
        database.execSQL(
                "CREATE INDEX index_world_entries_character ON " +
                        TABLE_WORLD_ENTRIES + "(character_id, position)"
        );
        seedBuiltIns(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
    }

    public List<CharacterProfile> getCharacters() {
        List<CharacterProfile> characters = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_CHARACTERS,
                new String[]{"id", "name", "description", "greeting", "system_prompt", "accent_color"},
                null,
                null,
                null,
                null,
                "created_at ASC"
        )) {
            while (cursor.moveToNext()) {
                characters.add(readCharacter(cursor, List.of()));
            }
        }
        return characters;
    }

    public CharacterProfile findById(String id) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_CHARACTERS,
                new String[]{"id", "name", "description", "greeting", "system_prompt", "accent_color"},
                "id = ?",
                new String[]{id},
                null,
                null,
                null,
                "1"
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return readCharacter(cursor, loadWorldEntries(id));
        }
    }

    public CharacterProfile getDefaultCharacter() {
        List<CharacterProfile> characters = getCharacters();
        if (characters.isEmpty()) {
            throw new IllegalStateException("角色库为空");
        }
        return findById(characters.get(0).getId());
    }

    public CharacterProfile importCard(CharacterCardData card) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            String existingId = findIdBySourceHash(database, card.getSourceHash());
            if (existingId != null) {
                database.setTransactionSuccessful();
                return findById(existingId);
            }

            String characterId = "imported-" + UUID.randomUUID();
            ContentValues characterValues = new ContentValues();
            characterValues.put("id", characterId);
            characterValues.put("name", card.getName());
            characterValues.put("description", card.getDescription());
            characterValues.put("greeting", card.getGreeting());
            characterValues.put("system_prompt", card.getSystemPrompt());
            characterValues.put("accent_color", accentColorFor(card.getName()));
            characterValues.put("source_hash", card.getSourceHash());
            characterValues.put("created_at", System.currentTimeMillis());
            database.insertOrThrow(TABLE_CHARACTERS, null, characterValues);

            int position = 0;
            for (WorldBookEntry entry : card.getWorldEntries()) {
                ContentValues worldValues = new ContentValues();
                worldValues.put("character_id", characterId);
                worldValues.put("keywords_json", new JSONArray(entry.getKeywords()).toString());
                worldValues.put("content", entry.getContent());
                worldValues.put("enabled", entry.isEnabled() ? 1 : 0);
                worldValues.put("constant_entry", entry.isConstant() ? 1 : 0);
                worldValues.put("position", position++);
                database.insertOrThrow(TABLE_WORLD_ENTRIES, null, worldValues);
            }
            database.setTransactionSuccessful();
            return new CharacterProfile(
                    characterId,
                    card.getName(),
                    card.getDescription(),
                    card.getGreeting(),
                    accentColorFor(card.getName()),
                    card.getSystemPrompt(),
                    card.getWorldEntries()
            );
        } finally {
            database.endTransaction();
        }
    }

    public CharacterProfile createCharacter(
            String name,
            String description,
            String greeting,
            String systemPrompt
    ) {
        String characterId = "local-" + UUID.randomUUID();
        int accentColor = accentColorFor(name);
        ContentValues values = new ContentValues();
        values.put("id", characterId);
        values.put("name", name);
        values.put("description", description);
        values.put("greeting", greeting);
        values.put("system_prompt", systemPrompt);
        values.put("accent_color", accentColor);
        values.put("source_hash", characterId);
        values.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertOrThrow(TABLE_CHARACTERS, null, values);
        return new CharacterProfile(
                characterId,
                name,
                description,
                greeting,
                accentColor,
                systemPrompt,
                List.of()
        );
    }

    public CharacterProfile updateCharacter(
            String characterId,
            String name,
            String description,
            String greeting,
            String systemPrompt
    ) {
        int accentColor = accentColorFor(name);
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("description", description);
        values.put("greeting", greeting);
        values.put("system_prompt", systemPrompt);
        values.put("accent_color", accentColor);
        int updatedRows = getWritableDatabase().update(
                TABLE_CHARACTERS,
                values,
                "id = ?",
                new String[]{characterId}
        );
        if (updatedRows != 1) {
            throw new IllegalArgumentException("角色不存在");
        }
        return findById(characterId);
    }

    private CharacterProfile readCharacter(Cursor cursor, List<WorldBookEntry> worldEntries) {
        return new CharacterProfile(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getString(3),
                cursor.getInt(5),
                cursor.getString(4),
                worldEntries
        );
    }

    private List<WorldBookEntry> loadWorldEntries(String characterId) {
        List<WorldBookEntry> entries = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_WORLD_ENTRIES,
                new String[]{"keywords_json", "content", "enabled", "constant_entry"},
                "character_id = ?",
                new String[]{characterId},
                null,
                null,
                "position ASC"
        )) {
            while (cursor.moveToNext()) {
                entries.add(new WorldBookEntry(
                        parseKeywords(cursor.getString(0)),
                        cursor.getString(1),
                        cursor.getInt(2) == 1,
                        cursor.getInt(3) == 1
                ));
            }
        }
        return entries;
    }

    private List<String> parseKeywords(String json) {
        List<String> keywords = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                String keyword = array.optString(index).trim();
                if (!keyword.isEmpty()) {
                    keywords.add(keyword);
                }
            }
        } catch (JSONException ignored) {
        }
        return keywords;
    }

    private String findIdBySourceHash(SQLiteDatabase database, String sourceHash) {
        try (Cursor cursor = database.query(
                TABLE_CHARACTERS,
                new String[]{"id"},
                "source_hash = ?",
                new String[]{sourceHash},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private void seedBuiltIns(SQLiteDatabase database) {
        insertBuiltIn(
                database,
                "librarian",
                "雨巷馆长",
                "安静、克制，擅长把混乱的想法整理成清晰计划。",
                "欢迎回来。先坐下，告诉我今天最想解决的一件事。",
                Color.rgb(92, 76, 153)
        );
        insertBuiltIn(
                database,
                "detective",
                "南城侦探",
                "善于追问事实，从细节中寻找被忽略的线索。",
                "案卷已经摊开了。你想从哪条线索开始？",
                Color.rgb(47, 93, 98)
        );
        insertBuiltIn(
                database,
                "coach",
                "灰塔教练",
                "不说空话，用可验证的小目标帮助你恢复行动。",
                "不用证明过去。说说你下一步准备交付什么。",
                Color.rgb(150, 86, 52)
        );
    }

    private void insertBuiltIn(
            SQLiteDatabase database,
            String id,
            String name,
            String description,
            String greeting,
            int accentColor
    ) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", name);
        values.put("description", description);
        values.put("greeting", greeting);
        values.put("system_prompt", description);
        values.put("accent_color", accentColor);
        values.put("source_hash", "builtin:" + id);
        values.put("created_at", 0);
        database.insertOrThrow(TABLE_CHARACTERS, null, values);
    }

    private int accentColorFor(String name) {
        int hash = name.hashCode();
        int red = 72 + Math.floorMod(hash, 112);
        int green = 72 + Math.floorMod(hash >> 8, 112);
        int blue = 72 + Math.floorMod(hash >> 16, 112);
        return Color.rgb(red, green, blue);
    }
}
