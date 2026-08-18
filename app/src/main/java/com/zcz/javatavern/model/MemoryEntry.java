package com.zcz.javatavern.model;

public final class MemoryEntry {
    private final String id;
    private final String content;
    private final long createdAt;

    public MemoryEntry(String id, String content, long createdAt) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
