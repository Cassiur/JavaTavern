package com.zcz.javatavern.model;

import java.util.List;

public final class CharacterCardData {
    private final String name;
    private final String description;
    private final String greeting;
    private final String systemPrompt;
    private final String sourceHash;
    private final String avatar;
    private final List<WorldBookEntry> worldEntries;

    public CharacterCardData(
            String name,
            String description,
            String greeting,
            String systemPrompt,
            String sourceHash,
            String avatar,
            List<WorldBookEntry> worldEntries
    ) {
        this.name = name;
        this.description = description;
        this.greeting = greeting;
        this.systemPrompt = systemPrompt;
        this.sourceHash = sourceHash;
        this.avatar = avatar == null ? "" : avatar;
        this.worldEntries = List.copyOf(worldEntries);
    }

    public CharacterCardData(
            String name,
            String description,
            String greeting,
            String systemPrompt,
            String sourceHash,
            List<WorldBookEntry> worldEntries
    ) {
        this(name, description, greeting, systemPrompt, sourceHash, "", worldEntries);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getGreeting() {
        return greeting;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getSourceHash() {
        return sourceHash;
    }

    /**
     * Avatar source marker, same convention as
     * {@link CharacterProfile#getAvatar()}.
     */
    public String getAvatar() {
        return avatar;
    }

    public List<WorldBookEntry> getWorldEntries() {
        return worldEntries;
    }
}
