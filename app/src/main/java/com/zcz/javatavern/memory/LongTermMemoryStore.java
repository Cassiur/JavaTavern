package com.zcz.javatavern.memory;

import android.content.Context;
import android.content.SharedPreferences;

import com.zcz.javatavern.model.MemoryEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LongTermMemoryStore {
    private static final String PREFERENCES_NAME = "confirmed_long_term_memories";
    private static final int MAX_ENTRIES = 50;
    private static final int MAX_ENTRY_CHARACTERS = 500;
    private static final int PROMPT_CHARACTER_LIMIT = 3000;
    private final SharedPreferences preferences;

    public LongTermMemoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public synchronized List<MemoryEntry> load(String characterId) {
        List<MemoryEntry> entries = new ArrayList<>();
        String serialized = preferences.getString(characterId, "[]");
        try {
            JSONArray array = new JSONArray(serialized);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.getJSONObject(index);
                entries.add(new MemoryEntry(
                        item.optString("id"),
                        item.optString("content"),
                        item.optLong("createdAt")
                ));
            }
        } catch (JSONException ignored) {
            preferences.edit().remove(characterId).apply();
        }
        return entries;
    }

    public synchronized MemoryEntry add(String characterId, String content) {
        String normalized = content.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        if (normalized.length() > MAX_ENTRY_CHARACTERS) {
            throw new IllegalArgumentException("单条记忆不能超过 500 字");
        }
        List<MemoryEntry> entries = load(characterId);
        if (entries.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("每个角色最多保存 50 条记忆");
        }
        MemoryEntry entry = new MemoryEntry(
                UUID.randomUUID().toString(),
                normalized,
                System.currentTimeMillis()
        );
        entries.add(entry);
        save(characterId, entries);
        return entry;
    }

    public synchronized void delete(String characterId, String memoryId) {
        List<MemoryEntry> entries = load(characterId);
        entries.removeIf(entry -> entry.getId().equals(memoryId));
        save(characterId, entries);
    }

    public synchronized String buildPrompt(String characterId) {
        return MemoryPromptFormatter.format(load(characterId), PROMPT_CHARACTER_LIMIT);
    }

    /** 全部角色的记忆，按角色 id 分组（备份导出用）。 */
    public synchronized Map<String, List<MemoryEntry>> loadAll() {
        Map<String, List<MemoryEntry>> result = new LinkedHashMap<>();
        // 先复制 key 集合：load() 遇到损坏条目会顺手删除，边遍历边改会抛异常。
        for (String characterId : new ArrayList<>(preferences.getAll().keySet())) {
            result.put(characterId, load(characterId));
        }
        return result;
    }

    /** 整体替换全部角色的记忆（备份恢复用）。 */
    public synchronized void replaceAll(Map<String, List<MemoryEntry>> memoriesByCharacter) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, List<MemoryEntry>> entry : memoriesByCharacter.entrySet()) {
            editor.putString(entry.getKey(), serialize(entry.getValue()));
        }
        editor.apply();
    }

    private void save(String characterId, List<MemoryEntry> entries) {
        preferences.edit().putString(characterId, serialize(entries)).apply();
    }

    private static String serialize(List<MemoryEntry> entries) {
        JSONArray array = new JSONArray();
        for (MemoryEntry entry : entries) {
            try {
                array.put(new JSONObject()
                        .put("id", entry.getId())
                        .put("content", entry.getContent())
                        .put("createdAt", entry.getCreatedAt()));
            } catch (JSONException exception) {
                throw new IllegalStateException("无法保存记忆", exception);
            }
        }
        return array.toString();
    }
}
