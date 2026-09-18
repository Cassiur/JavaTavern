package com.zcz.javatavern.model;

public class Chat {
    private final String id;
    private final String characterId;
    private final String name;
    private final long createdAt;

    public Chat(String id, String characterId, String name, long createdAt) {
        this.id = id;
        this.characterId = characterId;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCharacterId() {
        return characterId;
    }

    public String getName() {
        return name;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
