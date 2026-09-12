package com.zcz.javatavern.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ConversationDraftStore {
    private static final String PREFERENCES_NAME = "conversation_drafts";
    private final SharedPreferences preferences;

    public ConversationDraftStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public String load(String characterId) {
        return preferences.getString(characterId, "");
    }

    public void save(String characterId, String draft) {
        if (draft.isEmpty()) {
            clear(characterId);
            return;
        }
        preferences.edit().putString(characterId, draft).apply();
    }

    public void clear(String characterId) {
        preferences.edit().remove(characterId).apply();
    }

    /** 全部未发送草稿，按角色 id 分组（备份导出用）。 */
    public Map<String, String> loadAll() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (entry.getValue() instanceof String) {
                result.put(entry.getKey(), (String) entry.getValue());
            }
        }
        return result;
    }

    /** 整体替换全部草稿（备份恢复用）。 */
    public void replaceAll(Map<String, String> draftsByCharacter) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, String> entry : draftsByCharacter.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                editor.putString(entry.getKey(), entry.getValue());
            }
        }
        editor.apply();
    }
}
