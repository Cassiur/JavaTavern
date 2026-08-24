package com.zcz.javatavern.model;

import java.util.List;

public final class CharacterProfile {
    private final String id;
    private final String name;
    private final String description;
    private final String greeting;
    private final int accentColor;
    private final String systemPrompt;
    private final String avatar;
    private final List<WorldBookEntry> worldEntries;

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
        this.id = id;
        this.name = name;
        this.description = description;
        this.greeting = greeting;
        this.accentColor = accentColor;
        this.systemPrompt = systemPrompt;
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

    public String getGreeting() {
        return greeting;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public String getSystemPrompt() {
        return systemPrompt;
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
