package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.zcz.javatavern.model.Group;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 群聊的增删改查。群聊是多个角色同场对话的房间，成员为已存在角色的 id。
 */
public final class GroupRepository {
    private final TavernDatabase database;

    public GroupRepository(Context context) {
        this.database = TavernDatabase.get(context);
    }

    public Group createGroup(String name, List<String> memberIds) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("群聊名称不能为空");
        }
        String id = "group-" + UUID.randomUUID();
        long createdAt = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("name", trimmed);
        values.put("member_ids_json", new JSONArray(memberIds).toString());
        values.put("created_at", createdAt);
        database.getWritableDatabase().insertOrThrow(TavernDatabase.TABLE_GROUPS, null, values);
        return new Group(id, trimmed, List.copyOf(memberIds), createdAt);
    }

    public List<Group> listGroups() {
        List<Group> groups = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_GROUPS,
                new String[]{"id", "name", "member_ids_json", "created_at"},
                null, null, null, null,
                "created_at ASC"
        )) {
            while (cursor.moveToNext()) {
                groups.add(readGroup(cursor));
            }
        }
        return groups;
    }

    public Group findGroup(String id) {
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_GROUPS,
                new String[]{"id", "name", "member_ids_json", "created_at"},
                "id = ?",
                new String[]{id},
                null, null, null, "1"
        )) {
            return cursor.moveToFirst() ? readGroup(cursor) : null;
        }
    }

    public void updateGroup(String id, String name, List<String> memberIds) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("群聊名称不能为空");
        }
        ContentValues values = new ContentValues();
        values.put("name", trimmed);
        values.put("member_ids_json", new JSONArray(memberIds).toString());
        int updated = database.getWritableDatabase().update(
                TavernDatabase.TABLE_GROUPS, values, "id = ?", new String[]{id});
        if (updated != 1) {
            throw new IllegalArgumentException("群聊不存在");
        }
    }

    public void deleteGroup(String id) {
        database.getWritableDatabase().delete(
                TavernDatabase.TABLE_GROUPS, "id = ?", new String[]{id});
    }

    private Group readGroup(Cursor cursor) {
        return new Group(
                cursor.getString(0),
                cursor.getString(1),
                parseMemberIds(cursor.getString(2)),
                cursor.getLong(3)
        );
    }

    private List<String> parseMemberIds(String json) {
        List<String> ids = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int index = 0; index < array.length(); index++) {
                String id = array.optString(index).trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        } catch (JSONException ignored) {
        }
        return ids;
    }
}
