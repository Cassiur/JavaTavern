package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;

import com.zcz.javatavern.model.CharacterCardData;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.WorldBookEntry;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repository for characters and their world-book entries.
 *
 * <p>Reads and writes through the shared {@link TavernDatabase} singleton.
 */
public final class CharacterRepository {
    private final TavernDatabase database;

    public CharacterRepository(Context context) {
        this.database = TavernDatabase.get(context);
    }

    public List<CharacterProfile> getCharacters() {
        List<CharacterProfile> characters = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_CHARACTERS,
                new String[]{
                        "id", "name", "description", "personality", "scenario", "greeting",
                        "system_prompt", "post_history_instructions", "creator_notes",
                        "character_version", "mes_example", "alternate_greetings_json",
                        "accent_color", "avatar"
                },
                null,
                null,
                null,
                null,
                "created_at ASC"
        )) {
            while (cursor.moveToNext()) {
                String characterId = cursor.getString(0);
                characters.add(readCharacter(cursor, loadWorldEntries(characterId)));
            }
        }
        return characters;
    }

    public CharacterProfile findById(String id) {
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_CHARACTERS,
                new String[]{
                        "id", "name", "description", "personality", "scenario", "greeting",
                        "system_prompt", "post_history_instructions", "creator_notes",
                        "character_version", "mes_example", "alternate_greetings_json",
                        "accent_color", "avatar"
                },
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
        SQLiteDatabase writable = database.getWritableDatabase();
        writable.beginTransaction();
        try {
            String existingId = findIdBySourceHash(writable, card.getSourceHash());
            if (existingId != null) {
                writable.setTransactionSuccessful();
                return findById(existingId);
            }

            String characterId = "imported-" + UUID.randomUUID();
            int accentColor = accentColorFor(card.getName());
            ContentValues characterValues = new ContentValues();
            characterValues.put("id", characterId);
            characterValues.put("name", card.getName());
            characterValues.put("description", card.getDescription());
            characterValues.put("personality", card.getPersonality());
            characterValues.put("scenario", card.getScenario());
            characterValues.put("greeting", card.getGreeting());
            characterValues.put("system_prompt", card.getSystemPrompt());
            characterValues.put("post_history_instructions", card.getPostHistoryInstructions());
            characterValues.put("creator_notes", card.getCreatorNotes());
            characterValues.put("character_version", card.getCharacterVersion());
            characterValues.put("mes_example", card.getMesExample());
            characterValues.put("alternate_greetings_json", 
                    new JSONArray(card.getAlternateGreetings()).toString());
            characterValues.put("accent_color", accentColor);
            characterValues.put("source_hash", card.getSourceHash());
            characterValues.put("avatar", card.getAvatar() == null ? "" : card.getAvatar());
            characterValues.put("created_at", System.currentTimeMillis());
            writable.insertOrThrow(TavernDatabase.TABLE_CHARACTERS, null, characterValues);

            for (WorldBookEntry entry : card.getWorldEntries()) {
                writable.insertOrThrow(TavernDatabase.TABLE_WORLD_ENTRIES, null,
                        worldEntryValues(characterId, entry));
            }
            writable.setTransactionSuccessful();
            return new CharacterProfile(
                    characterId,
                    card.getName(),
                    card.getDescription(),
                    card.getPersonality(),
                    card.getScenario(),
                    card.getGreeting(),
                    accentColor,
                    card.getSystemPrompt(),
                    card.getPostHistoryInstructions(),
                    card.getCreatorNotes(),
                    card.getCharacterVersion(),
                    card.getMesExample(),
                    card.getAlternateGreetings(),
                    card.getAvatar() == null ? "" : card.getAvatar(),
                    card.getWorldEntries()
            );
        } finally {
            writable.endTransaction();
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
        values.put("personality", "");
        values.put("scenario", "");
        values.put("greeting", greeting);
        values.put("system_prompt", systemPrompt);
        values.put("post_history_instructions", "");
        values.put("creator_notes", "");
        values.put("character_version", "");
        values.put("mes_example", "");
        values.put("alternate_greetings_json", "[]");
        values.put("accent_color", accentColor);
        values.put("source_hash", characterId);
        values.put("avatar", "");
        values.put("created_at", System.currentTimeMillis());
        database.getWritableDatabase().insertOrThrow(TavernDatabase.TABLE_CHARACTERS, null, values);
        return new CharacterProfile(
                characterId,
                name,
                description,
                "",
                "",
                greeting,
                accentColor,
                systemPrompt,
                "",
                "",
                "",
                "",
                List.of(),
                "",
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
        int updatedRows = database.getWritableDatabase().update(
                TavernDatabase.TABLE_CHARACTERS,
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
                cursor.getString(0),   // id
                cursor.getString(1),   // name
                cursor.getString(2),   // description
                cursor.getString(3),   // personality
                cursor.getString(4),   // scenario
                cursor.getString(5),   // greeting
                cursor.getInt(12),     // accent_color
                cursor.getString(6),   // system_prompt
                cursor.getString(7),   // post_history_instructions
                cursor.getString(8),   // creator_notes
                cursor.getString(9),   // character_version
                cursor.getString(10),  // mes_example
                parseAlternateGreetings(cursor.getString(11)),  // alternate_greetings_json
                cursor.getString(13),  // avatar
                worldEntries
        );
    }

    private List<WorldBookEntry> loadWorldEntries(String characterId) {
        List<WorldBookEntry> entries = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_WORLD_ENTRIES,
                new String[]{
                        "id", "keywords_json", "secondary_keys_json", "content", "enabled", "constant_entry",
                        "position", "sort_order", "priority", "depth", "scan_depth",
                        "case_sensitive", "match_whole_words", "use_group_scoring", "automation_id",
                        "role", "vectorized", "sticky", "cooldown", "probability",
                        "exclude_recursion", "prevent_recursion"
                },
                "character_id = ?",
                new String[]{characterId},
                null,
                null,
                "position ASC, sort_order ASC, priority DESC"
        )) {
            while (cursor.moveToNext()) {
                entries.add(new WorldBookEntry(
                        cursor.getLong(0),      // id
                        parseKeywords(cursor.getString(1)),  // keywords_json
                        parseKeywords(cursor.getString(2)),  // secondary_keys_json
                        cursor.getString(3),    // content
                        cursor.getInt(4) == 1,  // enabled
                        cursor.getInt(5) == 1,  // constant_entry
                        cursor.getInt(6),       // position
                        cursor.getInt(7),       // sort_order
                        cursor.getInt(8),       // priority
                        cursor.getInt(9),       // depth
                        cursor.getInt(10),      // scan_depth
                        cursor.getInt(11) == 1, // case_sensitive
                        cursor.getInt(12) == 1, // match_whole_words
                        cursor.getInt(13) == 1, // use_group_scoring
                        cursor.getString(14),   // automation_id
                        cursor.getString(15),   // role
                        cursor.getInt(16) == 1, // vectorized
                        cursor.getInt(17),      // sticky
                        cursor.getInt(18),      // cooldown
                        cursor.getInt(19),      // probability
                        cursor.getInt(20) == 1, // exclude_recursion
                        cursor.getInt(21) == 1  // prevent_recursion
                ));
            }
        }
        return entries;
    }

    public void addWorldEntry(String characterId, WorldBookEntry entry) {
        database.getWritableDatabase().insertOrThrow(
                TavernDatabase.TABLE_WORLD_ENTRIES, null,
                worldEntryValues(characterId, entry));
    }

    public void updateWorldEntry(long entryId, WorldBookEntry entry) {
        int updated = database.getWritableDatabase().update(
                TavernDatabase.TABLE_WORLD_ENTRIES,
                worldEntryValues(null, entry),
                "id = ?",
                new String[]{String.valueOf(entryId)}
        );
        if (updated != 1) {
            throw new IllegalArgumentException("世界书条目不存在");
        }
    }

    public void deleteWorldEntry(long entryId) {
        database.getWritableDatabase().delete(
                TavernDatabase.TABLE_WORLD_ENTRIES, "id = ?",
                new String[]{String.valueOf(entryId)}
        );
    }

    private ContentValues worldEntryValues(String characterId, WorldBookEntry entry) {
        ContentValues values = new ContentValues();
        if (characterId != null) {
            values.put("character_id", characterId);
        }
        values.put("keywords_json", new JSONArray(entry.getKeywords()).toString());
        values.put("secondary_keys_json", new JSONArray(entry.getSecondaryKeys()).toString());
        values.put("content", entry.getContent());
        values.put("enabled", entry.isEnabled() ? 1 : 0);
        values.put("constant_entry", entry.isConstant() ? 1 : 0);
        values.put("position", entry.getPosition());
        values.put("sort_order", entry.getOrder());
        values.put("priority", entry.getPriority());
        values.put("depth", entry.getDepth());
        values.put("scan_depth", entry.getScanDepth());
        values.put("case_sensitive", entry.isCaseSensitive() ? 1 : 0);
        values.put("match_whole_words", entry.isMatchWholeWords() ? 1 : 0);
        values.put("use_group_scoring", entry.isUseGroupScoring() ? 1 : 0);
        values.put("automation_id", entry.getAutomationId());
        values.put("role", entry.getRole());
        values.put("vectorized", entry.isVectorized() ? 1 : 0);
        values.put("sticky", entry.getSticky());
        values.put("cooldown", entry.getCooldown());
        values.put("probability", entry.getProbability());
        values.put("exclude_recursion", entry.isExcludeRecursion() ? 1 : 0);
        values.put("prevent_recursion", entry.isPreventRecursion() ? 1 : 0);
        return values;
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

    private List<String> parseAlternateGreetings(String json) {
        List<String> greetings = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return greetings;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                String greeting = array.optString(index).trim();
                if (!greeting.isEmpty()) {
                    greetings.add(greeting);
                }
            }
        } catch (JSONException ignored) {
        }
        return greetings;
    }

    private String findIdBySourceHash(SQLiteDatabase writable, String sourceHash) {
        try (Cursor cursor = writable.query(
                TavernDatabase.TABLE_CHARACTERS,
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

    private int accentColorFor(String name) {
        int hash = name.hashCode();
        int red = 72 + Math.floorMod(hash, 112);
        int green = 72 + Math.floorMod(hash >> 8, 112);
        int blue = 72 + Math.floorMod(hash >> 16, 112);
        return Color.rgb(red, green, blue);
    }

    /** No-op — the shared database is process-scoped and must not be closed. */
    public void close() {
    }
}
