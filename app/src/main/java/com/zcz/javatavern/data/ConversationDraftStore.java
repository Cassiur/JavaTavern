package com.zcz.javatavern.data;

import android.content.Context;
import android.content.SharedPreferences;

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
}
