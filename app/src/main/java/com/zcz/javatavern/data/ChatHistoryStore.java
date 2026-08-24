package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.zcz.javatavern.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for chat messages and agent audit records.
 *
 * <p>Reads and writes through the shared {@link TavernDatabase} singleton.
 */
public final class ChatHistoryStore {
    private static final String TABLE_MESSAGES = TavernDatabase.TABLE_MESSAGES;
    private static final String TABLE_AGENT_AUDIT = TavernDatabase.TABLE_AGENT_AUDIT;
    private static final String TABLE_MESSAGES_FTS = TavernDatabase.TABLE_MESSAGES_FTS;
    private static final String[] MESSAGE_COLUMNS = new String[]{
            "id", "role", "kind", "title", "content", "created_at",
            "action_token", "action_type", "action_state",
            "attachment_path", "attachment_mime_type",
            "reply_to_message_id", "reply_preview", "reaction", "speaker_name"
    };

    private final TavernDatabase database;

    public ChatHistoryStore(Context context) {
        this.database = TavernDatabase.get(context);
    }

    public List<ChatMessage> loadMessages(String characterId) {
        List<ChatMessage> messages = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                MESSAGE_COLUMNS,
                "character_id = ?",
                new String[]{characterId},
                null,
                null,
                "created_at ASC"
        )) {
            readMessages(cursor, messages);
        }
        return messages;
    }

    public List<ChatMessage> loadRecentMessages(String characterId, int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                MESSAGE_COLUMNS,
                "character_id = ?",
                new String[]{characterId},
                null,
                null,
                "id DESC",
                Integer.toString(limit)
        )) {
            readMessages(cursor, messages);
        }
        java.util.Collections.reverse(messages);
        return messages;
    }

    /**
     * Returns the most recent message for every character that has at least
     * one message, keyed by character ID. Characters without messages are
     * absent from the map. Uses a single grouped query instead of one query
     * per character.
     */
    public java.util.Map<String, ChatMessage> loadLatestMessagesByCharacter() {
        java.util.Map<String, ChatMessage> latest = new java.util.LinkedHashMap<>();
        String columns = String.join(", ", MESSAGE_COLUMNS);
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT " + columns + ", character_id FROM " + TABLE_MESSAGES +
                        " WHERE id IN (SELECT MAX(id) FROM " + TABLE_MESSAGES +
                        " GROUP BY character_id)",
                null)) {
            while (cursor.moveToNext()) {
                String characterId = cursor.getString(MESSAGE_COLUMNS.length);
                latest.put(characterId, readMessage(cursor));
            }
        }
        return latest;
    }

    public List<ChatMessage> loadMessagesBefore(String characterId, long beforeId, int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                MESSAGE_COLUMNS,
                "character_id = ? AND id < ?",
                new String[]{characterId, Long.toString(beforeId)},
                null,
                null,
                "id DESC",
                Integer.toString(limit)
        )) {
            readMessages(cursor, messages);
        }
        java.util.Collections.reverse(messages);
        return messages;
    }

    public List<ChatMessage> searchMessages(String characterId, String query, int limit) {
        String normalized = query.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> messages = new ArrayList<>();
        String matchExpression = "\"" + normalized.replace("\"", "\"\"") + "\"";
        try (Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT m.id, m.role, m.kind, m.title, m.content, m.created_at, " +
                        "m.action_token, m.action_type, m.action_state, " +
                        "m.attachment_path, m.attachment_mime_type, " +
                        "m.reply_to_message_id, m.reply_preview, m.reaction, m.speaker_name " +
                        "FROM " + TABLE_MESSAGES_FTS + " f JOIN " + TABLE_MESSAGES +
                        " m ON m.id = CAST(f.message_id AS INTEGER) " +
                        "WHERE f.character_id = ? AND f.content MATCH ? " +
                        "ORDER BY m.id DESC LIMIT ?",
                new String[]{characterId, matchExpression, Integer.toString(limit)}
        )) {
            readMessages(cursor, messages);
        } catch (SQLiteException ignored) {
            return searchMessagesWithLike(characterId, normalized, limit);
        }
        if (messages.isEmpty()) {
            return searchMessagesWithLike(characterId, normalized, limit);
        }
        return messages;
    }

    public List<ChatMessage> loadMessageContext(String characterId, long targetId, int radius) {
        List<ChatMessage> beforeAndTarget = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                MESSAGE_COLUMNS,
                "character_id = ? AND id <= ?",
                new String[]{characterId, Long.toString(targetId)},
                null,
                null,
                "id DESC",
                Integer.toString(radius + 1)
        )) {
            readMessages(cursor, beforeAndTarget);
        }
        java.util.Collections.reverse(beforeAndTarget);

        List<ChatMessage> after = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                MESSAGE_COLUMNS,
                "character_id = ? AND id > ?",
                new String[]{characterId, Long.toString(targetId)},
                null,
                null,
                "id ASC",
                Integer.toString(radius)
        )) {
            readMessages(cursor, after);
        }
        beforeAndTarget.addAll(after);
        return beforeAndTarget;
    }

    public long addMessage(String characterId, ChatMessage.Role role, String content, long createdAt) {
        return addMessage(
                characterId,
                role,
                ChatMessage.Kind.TEXT,
                "",
                content,
                createdAt,
                "",
                "",
                ChatMessage.ActionState.NONE
        );
    }

    public long addMessage(
            String characterId,
            ChatMessage.Role role,
            ChatMessage.Kind kind,
            String title,
            String content,
            long createdAt
    ) {
        return addMessage(
                characterId,
                role,
                kind,
                title,
                content,
                createdAt,
                "",
                "",
                ChatMessage.ActionState.NONE
        );
    }

    public long addMessage(
            String characterId,
            ChatMessage.Role role,
            ChatMessage.Kind kind,
            String title,
            String content,
            long createdAt,
            String actionToken,
            String actionType,
            ChatMessage.ActionState actionState
    ) {
        return addMessage(
                characterId,
                role,
                kind,
                title,
                content,
                createdAt,
                actionToken,
                actionType,
                actionState,
                "",
                ""
        );
    }

    public long addMessage(
            String characterId,
            ChatMessage.Role role,
            ChatMessage.Kind kind,
            String title,
            String content,
            long createdAt,
            String actionToken,
            String actionType,
            ChatMessage.ActionState actionState,
            String attachmentPath,
            String attachmentMimeType
    ) {
        return addMessage(
                characterId,
                role,
                kind,
                title,
                content,
                createdAt,
                actionToken,
                actionType,
                actionState,
                attachmentPath,
                attachmentMimeType,
                -1,
                "",
                ""
        );
    }

    public long addMessage(
            String characterId,
            ChatMessage.Role role,
            ChatMessage.Kind kind,
            String title,
            String content,
            long createdAt,
            String actionToken,
            String actionType,
            ChatMessage.ActionState actionState,
            String attachmentPath,
            String attachmentMimeType,
            long replyToMessageId,
            String replyPreview,
            String reaction
    ) {
        ContentValues values = new ContentValues();
        values.put("character_id", characterId);
        values.put("role", role.name());
        values.put("kind", kind.name());
        values.put("title", title);
        values.put("action_token", actionToken);
        values.put("action_type", actionType);
        values.put("action_state", actionState.name());
        values.put("attachment_path", attachmentPath);
        values.put("attachment_mime_type", attachmentMimeType);
        values.put("reply_to_message_id", replyToMessageId);
        values.put("reply_preview", replyPreview);
        values.put("reaction", reaction);
        values.put("content", content);
        values.put("created_at", createdAt);
        return database.getWritableDatabase().insertOrThrow(TABLE_MESSAGES, null, values);
    }

    /** 群聊消息：额外记录发言角色（speaker）。characterId 传入群 id。 */
    public long addGroupMessage(
            String characterId,
            ChatMessage.Role role,
            String content,
            long createdAt,
            String speakerId,
            String speakerName
    ) {
        ContentValues values = new ContentValues();
        values.put("character_id", characterId);
        values.put("role", role.name());
        values.put("kind", ChatMessage.Kind.TEXT.name());
        values.put("title", "");
        values.put("content", content);
        values.put("created_at", createdAt);
        values.put("speaker_id", speakerId);
        values.put("speaker_name", speakerName);
        return database.getWritableDatabase().insertOrThrow(TABLE_MESSAGES, null, values);
    }

    public List<String> loadAttachmentPaths(String characterId) {
        List<String> attachmentPaths = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                new String[]{"attachment_path"},
                "character_id = ? AND attachment_path != ''",
                new String[]{characterId},
                null,
                null,
                null
        )) {
            while (cursor.moveToNext()) {
                attachmentPaths.add(cursor.getString(0));
            }
        }
        return attachmentPaths;
    }

    public void clearMessages(String characterId) {
        database.getWritableDatabase().delete(
                TABLE_MESSAGES,
                "character_id = ?",
                new String[]{characterId}
        );
    }

    public void updateMessageContent(long messageId, String content) {
        ContentValues values = new ContentValues();
        values.put("content", content);
        int updatedRows = database.getWritableDatabase().update(
                TABLE_MESSAGES,
                values,
                "id = ?",
                new String[]{Long.toString(messageId)}
        );
        if (updatedRows != 1) {
            throw new IllegalArgumentException("消息不存在");
        }
    }

    public void deleteMessage(long messageId) {
        int deletedRows = database.getWritableDatabase().delete(
                TABLE_MESSAGES,
                "id = ?",
                new String[]{Long.toString(messageId)}
        );
        if (deletedRows != 1) {
            throw new IllegalArgumentException("消息不存在");
        }
    }

    public void updateMessageReaction(long messageId, String reaction) {
        ContentValues values = new ContentValues();
        values.put("reaction", reaction);
        int updatedRows = database.getWritableDatabase().update(
                TABLE_MESSAGES,
                values,
                "id = ?",
                new String[]{Long.toString(messageId)}
        );
        if (updatedRows != 1) {
            throw new IllegalArgumentException("消息不存在");
        }
    }

    public ChatMessage loadPreviousUserMessage(String characterId, long beforeMessageId) {
        List<ChatMessage> messages = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                MESSAGE_COLUMNS,
                "character_id = ? AND role = ? AND id < ?",
                new String[]{
                        characterId,
                        ChatMessage.Role.USER.name(),
                        Long.toString(beforeMessageId)
                },
                null,
                null,
                "id DESC",
                "1"
        )) {
            readMessages(cursor, messages);
        }
        return messages.isEmpty() ? null : messages.get(0);
    }

    public void updateActionState(String actionToken, ChatMessage.ActionState actionState) {
        ContentValues values = new ContentValues();
        values.put("action_state", actionState.name());
        database.getWritableDatabase().update(
                TABLE_MESSAGES,
                values,
                "action_token = ?",
                new String[]{actionToken}
        );
    }

    public void addAgentAudit(
            String characterId,
            String actionToken,
            String actionType,
            String state,
            String detail,
            long createdAt
    ) {
        ContentValues values = new ContentValues();
        values.put("character_id", characterId);
        values.put("action_token", actionToken);
        values.put("action_type", actionType);
        values.put("state", state);
        values.put("detail", detail);
        values.put("created_at", createdAt);
        database.getWritableDatabase().insertOrThrow(TABLE_AGENT_AUDIT, null, values);
    }

    public long clearConversationAndAddAgentResult(
            String characterId,
            String actionToken,
            String actionType,
            String title,
            String content,
            long createdAt
    ) {
        SQLiteDatabase writable = database.getWritableDatabase();
        writable.beginTransaction();
        try {
            insertAgentAudit(
                    writable,
                    characterId,
                    actionToken,
                    actionType,
                    ChatMessage.ActionState.CONFIRMED.name(),
                    "用户确认执行",
                    createdAt
            );
            writable.delete(
                    TABLE_MESSAGES,
                    "character_id = ?",
                    new String[]{characterId}
            );

            ContentValues resultValues = new ContentValues();
            resultValues.put("character_id", characterId);
            resultValues.put("role", ChatMessage.Role.ASSISTANT.name());
            resultValues.put("kind", ChatMessage.Kind.AGENT_RESULT.name());
            resultValues.put("title", title);
            resultValues.put("action_token", actionToken);
            resultValues.put("action_type", actionType);
            resultValues.put("action_state", ChatMessage.ActionState.SUCCEEDED.name());
            resultValues.put("attachment_path", "");
            resultValues.put("attachment_mime_type", "");
            resultValues.put("content", content);
            resultValues.put("created_at", createdAt);
            long resultId = writable.insertOrThrow(TABLE_MESSAGES, null, resultValues);

            insertAgentAudit(
                    writable,
                    characterId,
                    actionToken,
                    actionType,
                    ChatMessage.ActionState.SUCCEEDED.name(),
                    content,
                    createdAt
            );
            writable.setTransactionSuccessful();
            return resultId;
        } finally {
            writable.endTransaction();
        }
    }

    private List<ChatMessage> searchMessagesWithLike(String characterId, String query, int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE_MESSAGES,
                MESSAGE_COLUMNS,
                "character_id = ? AND content LIKE ?",
                new String[]{characterId, "%" + query + "%"},
                null,
                null,
                "id DESC",
                Integer.toString(limit)
        )) {
            readMessages(cursor, messages);
        }
        return messages;
    }

    private void readMessages(Cursor cursor, List<ChatMessage> output) {
        while (cursor.moveToNext()) {
            output.add(readMessage(cursor));
        }
    }

    private ChatMessage readMessage(Cursor cursor) {
        return new ChatMessage(
                cursor.getLong(0),
                ChatMessage.Role.valueOf(cursor.getString(1)),
                ChatMessage.Kind.valueOf(cursor.getString(2)),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getLong(5),
                cursor.getString(6),
                cursor.getString(7),
                ChatMessage.ActionState.valueOf(cursor.getString(8)),
                cursor.getString(9),
                cursor.getString(10),
                cursor.getLong(11),
                cursor.getString(12),
                cursor.getString(13),
                cursor.getString(14)
        );
    }

    private void insertAgentAudit(
            SQLiteDatabase database,
            String characterId,
            String actionToken,
            String actionType,
            String state,
            String detail,
            long createdAt
    ) {
        ContentValues values = new ContentValues();
        values.put("character_id", characterId);
        values.put("action_token", actionToken);
        values.put("action_type", actionType);
        values.put("state", state);
        values.put("detail", detail);
        values.put("created_at", createdAt);
        database.insertOrThrow(TABLE_AGENT_AUDIT, null, values);
    }

    /** No-op — the shared database is process-scoped and must not be closed. */
    public void close() {
    }
}
