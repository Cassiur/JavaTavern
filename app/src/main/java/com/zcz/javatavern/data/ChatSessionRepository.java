package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.database.Cursor;
import com.zcz.javatavern.model.Chat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatSessionRepository {
    private final TavernDatabase database;

    public ChatSessionRepository(TavernDatabase database) {
        this.database = database;
    }

    public List<Chat> getChats(String characterId) {
        List<Chat> chats = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_CHATS,
                new String[]{"id", "character_id", "name", "created_at"},
                "character_id = ?",
                new String[]{characterId},
                null,
                null,
                "created_at DESC"
        )) {
            while (cursor.moveToNext()) {
                chats.add(new Chat(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3)
                ));
            }
        }
        return chats;
    }

    public Chat getDefaultChat(String characterId) {
        String defaultChatId = "default-" + characterId;
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_CHATS,
                new String[]{"id", "character_id", "name", "created_at"},
                "id = ?",
                new String[]{defaultChatId},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return new Chat(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3)
                );
            }
        }
        return createDefaultChat(characterId);
    }

    private Chat createDefaultChat(String characterId) {
        String chatId = "default-" + characterId;
        ContentValues values = new ContentValues();
        values.put("id", chatId);
        values.put("character_id", characterId);
        values.put("name", "默认聊天");
        values.put("created_at", System.currentTimeMillis());
        database.getWritableDatabase().insertOrThrow(TavernDatabase.TABLE_CHATS, null, values);
        return new Chat(chatId, characterId, "默认聊天", System.currentTimeMillis());
    }

    public Chat createChat(String characterId, String name) {
        String chatId = "chat-" + UUID.randomUUID();
        ContentValues values = new ContentValues();
        values.put("id", chatId);
        values.put("character_id", characterId);
        values.put("name", name);
        long createdAt = System.currentTimeMillis();
        values.put("created_at", createdAt);
        database.getWritableDatabase().insertOrThrow(TavernDatabase.TABLE_CHATS, null, values);
        return new Chat(chatId, characterId, name, createdAt);
    }

    public void renameChat(String chatId, String newName) {
        ContentValues values = new ContentValues();
        values.put("name", newName);
        database.getWritableDatabase().update(
                TavernDatabase.TABLE_CHATS,
                values,
                "id = ?",
                new String[]{chatId}
        );
    }

    public void deleteChat(String chatId) {
        if (chatId.startsWith("default-")) {
            throw new IllegalStateException("Cannot delete default chat");
        }
        database.getWritableDatabase().beginTransaction();
        try {
            database.getWritableDatabase().delete(
                    TavernDatabase.TABLE_MESSAGES,
                    "chat_id = ?",
                    new String[]{chatId}
            );
            database.getWritableDatabase().delete(
                    TavernDatabase.TABLE_CHATS,
                    "id = ?",
                    new String[]{chatId}
            );
            database.getWritableDatabase().setTransactionSuccessful();
        } finally {
            database.getWritableDatabase().endTransaction();
        }
    }

    public Chat findById(String chatId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_CHATS,
                new String[]{"id", "character_id", "name", "created_at"},
                "id = ?",
                new String[]{chatId},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return new Chat(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getLong(3)
                );
            }
        }
        return null;
    }
}
