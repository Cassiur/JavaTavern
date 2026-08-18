package com.zcz.javatavern.model;

import java.util.List;

public final class CharacterProfile {
    private final String id;
    private final String name;
    private final String description;
    private final String greeting;
    private final int accentColor;
    private final String systemPrompt;
    private final List<WorldBookEntry> worldEntries;

    public CharacterProfile(
            String id,
            String name,
            String description,
            String greeting,
            int accentColor
    ) {
        this(id, name, description, greeting, accentColor, description, List.of());
    }

    public CharacterProfile(
            String id,
            String name,
            String description,
            String greeting,
            int accentColor,
            String systemPrompt,
            List<WorldBookEntry> worldEntries
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.greeting = greeting;
        this.accentColor = accentColor;
        this.systemPrompt = systemPrompt;
        this.worldEntries = List.copyOf(worldEntries);
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

    public String getGreeting() {
        return greeting;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<WorldBookEntry> getWorldEntries() {
        return worldEntries;
    }
}
