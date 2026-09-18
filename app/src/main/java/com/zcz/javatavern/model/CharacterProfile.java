package com.zcz.javatavern.model;

import java.util.List;

public final class CharacterProfile {
    private final String id;
    private final String name;
    private final String description;
    private final String personality;
    private final String scenario;
    private final String greeting;
    private final int accentColor;
    private final String systemPrompt;
    private final String postHistoryInstructions;
    private final String creatorNotes;
    private final String characterVersion;
    private final String mesExample;
    private final List<String> alternateGreetings;
    private final String avatar;
    private final List<WorldBookEntry> worldEntries;

    public CharacterProfile(
            String id,
            String name,
            String description,
            String personality,
            String scenario,
            String greeting,
            int accentColor,
            String systemPrompt,
            String postHistoryInstructions,
            String creatorNotes,
            String characterVersion,
            String mesExample,
            List<String> alternateGreetings,
            String avatar,
            List<WorldBookEntry> worldEntries
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.personality = personality == null ? "" : personality;
        this.scenario = scenario == null ? "" : scenario;
        this.greeting = greeting;
        this.accentColor = accentColor;
        this.systemPrompt = systemPrompt;
        this.postHistoryInstructions = postHistoryInstructions == null ? "" : postHistoryInstructions;
        this.creatorNotes = creatorNotes == null ? "" : creatorNotes;
        this.characterVersion = characterVersion == null ? "" : characterVersion;
        this.mesExample = mesExample == null ? "" : mesExample;
        this.alternateGreetings = alternateGreetings == null ? List.of() : List.copyOf(alternateGreetings);
        this.avatar = avatar == null ? "" : avatar;
        this.worldEntries = List.copyOf(worldEntries);
    }

    public CharacterProfile(
            String id,
            String name,
            String description,
            String greeting,
            int accentColor,
            String systemPrompt,
            String avatar,
            List<WorldBookEntry> worldEntries
    ) {
        this(id, name, description, "", "", greeting, accentColor, systemPrompt,
                "", "", "", "", List.of(), avatar, worldEntries);
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
        this(id, name, description, greeting, accentColor, systemPrompt, "", worldEntries);
    }

    public CharacterProfile(
            String id,
            String name,
            String description,
            String greeting,
            int accentColor
    ) {
        this(id, name, description, greeting, accentColor, description, "", List.of());
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

    public String getPersonality() {
        return personality;
    }

    public String getScenario() {
        return scenario;
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

    public String getPostHistoryInstructions() {
        return postHistoryInstructions;
    }

    public String getCreatorNotes() {
        return creatorNotes;
    }

    public String getCharacterVersion() {
        return characterVersion;
    }

    public String getMesExample() {
        return mesExample;
    }

    public List<String> getAlternateGreetings() {
        return alternateGreetings;
    }

    /**
     * Avatar source marker.
     * <ul>
     *   <li>{@code "res:<drawable-name>"} → built-in drawable resource
     *       (resolved via {@code Resources.getIdentifier}).</li>
     *   <li>An absolute file path → decoded directly (used for imported PNG
     *       character cards whose embedded portrait was extracted to
     *       {@code filesDir/avatars/}).</li>
     *   <li>Empty → no avatar; the UI falls back to a colored initial.</li>
     * </ul>
     */
    public String getAvatar() {
        return avatar;
    }

    public List<WorldBookEntry> getWorldEntries() {
        return worldEntries;
    }
}
