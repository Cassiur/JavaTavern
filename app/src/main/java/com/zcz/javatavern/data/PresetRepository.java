package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 生成预设的增删查。预设是采样参数的命名快照，应用时由 UI 把参数复制回
 * 当前模型设置（见 {@code SettingsActivity}），本仓库只负责持久化。
 */
public final class PresetRepository {
    private final TavernDatabase database;

    public PresetRepository(Context context) {
        this.database = TavernDatabase.get(context);
    }

    public List<GenerationPreset> listPresets() {
        List<GenerationPreset> presets = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_PRESETS,
                new String[]{"id", "name", "temperature", "top_p", "max_tokens",
                        "frequency_penalty", "presence_penalty"},
                null, null, null, null,
                "created_at ASC, name ASC"
        )) {
            while (cursor.moveToNext()) {
                presets.add(readPreset(cursor));
            }
        }
        return presets;
    }

    /**
     * 保存预设：同名则覆盖参数，否则新增。
     */
    public void savePreset(String name, GenerationParams params) {
        String normalizedName = name.trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("预设名称不能为空");
        }
        SQLiteDatabase writable = database.getWritableDatabase();
        String existingId = findIdByName(normalizedName);
        ContentValues values = paramValues(normalizedName, params);
        if (existingId == null) {
            values.put("id", "preset-" + UUID.randomUUID());
            values.put("created_at", System.currentTimeMillis());
            writable.insertOrThrow(TavernDatabase.TABLE_PRESETS, null, values);
        } else {
            writable.update(TavernDatabase.TABLE_PRESETS, values,
                    "id = ?", new String[]{existingId});
        }
    }

    public void deletePreset(String id) {
        database.getWritableDatabase().delete(
                TavernDatabase.TABLE_PRESETS, "id = ?", new String[]{id});
    }

    private GenerationPreset readPreset(Cursor cursor) {
        return new GenerationPreset(
                cursor.getString(0),
                cursor.getString(1),
                new GenerationParams(
                        readDouble(cursor, 2),
                        readDouble(cursor, 3),
                        readInt(cursor, 4),
                        readDouble(cursor, 5),
                        readDouble(cursor, 6)
                )
        );
    }

    private ContentValues paramValues(String name, GenerationParams params) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        putNullable(values, "temperature", params.getTemperature());
        putNullable(values, "top_p", params.getTopP());
        putNullable(values, "max_tokens", params.getMaxTokens());
        putNullable(values, "frequency_penalty", params.getFrequencyPenalty());
        putNullable(values, "presence_penalty", params.getPresencePenalty());
        return values;
    }

    private void putNullable(ContentValues values, String key, Number value) {
        if (value == null) {
            values.putNull(key);
        } else {
            values.put(key, String.valueOf(value));
        }
    }

    private Double readDouble(Cursor cursor, int column) {
        return cursor.isNull(column) ? null : Double.parseDouble(cursor.getString(column));
    }

    private Integer readInt(Cursor cursor, int column) {
        return cursor.isNull(column) ? null : Integer.parseInt(cursor.getString(column));
    }

    private String findIdByName(String name) {
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_PRESETS,
                new String[]{"id"},
                "name = ?",
                new String[]{name},
                null, null, null, "1"
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }
}
