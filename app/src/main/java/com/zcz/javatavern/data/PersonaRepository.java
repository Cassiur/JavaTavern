package com.zcz.javatavern.data;

import android.content.ContentValues;
import android.database.Cursor;
import com.zcz.javatavern.model.Persona;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PersonaRepository {
    private final TavernDatabase database;

    public PersonaRepository(TavernDatabase database) {
        this.database = database;
    }

    public List<Persona> getPersonas() {
        List<Persona> personas = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_PERSONAS,
                new String[]{"id", "name", "description", "is_default", "avatar"},
                null,
                null,
                null,
                null,
                "created_at ASC"
        )) {
            while (cursor.moveToNext()) {
                personas.add(new Persona(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3) == 1,
                        cursor.getString(4)
                ));
            }
        }
        return personas;
    }

    public Persona getDefaultPersona() {
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_PERSONAS,
                new String[]{"id", "name", "description", "is_default", "avatar"},
                "is_default = 1",
                null,
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return new Persona(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        true,
                        cursor.getString(4)
                );
            }
        }
        return createDefaultPersona();
    }

    private Persona createDefaultPersona() {
        String personaId = "default-" + UUID.randomUUID();
        ContentValues values = new ContentValues();
        values.put("id", personaId);
        values.put("name", "默认用户");
        values.put("description", "");
        values.put("is_default", 1);
        values.put("avatar", "");
        values.put("created_at", System.currentTimeMillis());
        database.getWritableDatabase().insertOrThrow(TavernDatabase.TABLE_PERSONAS, null, values);
        return new Persona(personaId, "默认用户", "", true, "");
    }

    public Persona createPersona(String name, String description) {
        String personaId = "persona-" + UUID.randomUUID();
        ContentValues values = new ContentValues();
        values.put("id", personaId);
        values.put("name", name);
        values.put("description", description);
        values.put("is_default", 0);
        values.put("avatar", "");
        values.put("created_at", System.currentTimeMillis());
        database.getWritableDatabase().insertOrThrow(TavernDatabase.TABLE_PERSONAS, null, values);
        return new Persona(personaId, name, description, false, "");
    }

    public void setDefaultPersona(String personaId) {
        database.getWritableDatabase().beginTransaction();
        try {
            ContentValues resetValues = new ContentValues();
            resetValues.put("is_default", 0);
            database.getWritableDatabase().update(
                    TavernDatabase.TABLE_PERSONAS,
                    resetValues,
                    null,
                    null
            );
            ContentValues setValues = new ContentValues();
            setValues.put("is_default", 1);
            database.getWritableDatabase().update(
                    TavernDatabase.TABLE_PERSONAS,
                    setValues,
                    "id = ?",
                    new String[]{personaId}
            );
            database.getWritableDatabase().setTransactionSuccessful();
        } finally {
            database.getWritableDatabase().endTransaction();
        }
    }

    public void deletePersona(String personaId) {
        Persona persona = findById(personaId);
        if (persona != null && persona.isDefault()) {
            throw new IllegalStateException("Cannot delete default persona");
        }
        database.getWritableDatabase().delete(
                TavernDatabase.TABLE_PERSONAS,
                "id = ?",
                new String[]{personaId}
        );
    }

    public Persona findById(String personaId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                TavernDatabase.TABLE_PERSONAS,
                new String[]{"id", "name", "description", "is_default", "avatar"},
                "id = ?",
                new String[]{personaId},
                null,
                null,
                null,
                "1"
        )) {
            if (cursor.moveToFirst()) {
                return new Persona(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getInt(3) == 1,
                        cursor.getString(4)
                );
            }
        }
        return null;
    }

    public void updatePersona(String personaId, String name, String description) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("description", description);
        database.getWritableDatabase().update(
                TavernDatabase.TABLE_PERSONAS,
                values,
                "id = ?",
                new String[]{personaId}
        );
    }
}
