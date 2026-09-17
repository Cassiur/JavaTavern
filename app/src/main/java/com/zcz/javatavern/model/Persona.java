package com.zcz.javatavern.model;

public class Persona {
    private final String id;
    private final String name;
    private final String description;
    private final boolean isDefault;
    private final String avatar;

    public Persona(String id, String name, String description, boolean isDefault, String avatar) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isDefault = isDefault;
        this.avatar = avatar;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public String getAvatar() {
        return avatar;
    }
}
