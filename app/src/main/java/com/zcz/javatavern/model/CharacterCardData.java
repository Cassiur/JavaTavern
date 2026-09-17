package com.zcz.javatavern.model;

import java.util.List;

/**
 * Parsed character card, kept as SillyTavern-style separate fields rather
 * than one concatenated block.
 *
 * <p>{@code systemPrompt} here is the card's own raw {@code system_prompt}
 * field only (not description/personality/scenario glued together) —
 * assembling those into the actual request prompt is
 * {@link com.zcz.javatavern.network.OpenAiCompatibleClient}'s job, done at
 * generation time, so the pieces stay independently editable/exportable.
 */
public final class CharacterCardData {
    private final String name;
    private final String description;
    private final String personality;
    private final String scenario;
    private final String greeting;
    private final String systemPrompt;
    private final String postHistoryInstructions;
    private final String creatorNotes;
    private final String characterVersion;
    private final String mesExample;
    private final List<String> alternateGreetings;
    private final String sourceHash;
    private final String avatar;
    private final List<WorldBookEntry> worldEntries;

    public CharacterCardData(
            String name,
            String description,
            String personality,
            String scenario,
            String greeting,
            String systemPrompt,
            String postHistoryInstructions,
            String creatorNotes,
            String characterVersion,
            String mesExample,
            List<String> alternateGreetings,
            String sourceHash,
            String avatar,
            List<WorldBookEntry> worldEntries
    ) {
        this.name = name;
        this.description = description;
        this.personality = personality == null ? "" : personality;
        this.scenario = scenario == null ? "" : scenario;
        this.greeting = greeting;
        this.systemPrompt = systemPrompt;
        this.postHistoryInstructions = postHistoryInstructions == null ? "" : postHistoryInstructions;
        this.creatorNotes = creatorNotes == null ? "" : creatorNotes;
        this.characterVersion = characterVersion == null ? "" : characterVersion;
        this.mesExample = mesExample == null ? "" : mesExample;
        this.alternateGreetings = alternateGreetings == null ? List.of() : List.copyOf(alternateGreetings);
        this.sourceHash = sourceHash;
        this.avatar = avatar == null ? "" : avatar;
        this.worldEntries = List.copyOf(worldEntries);
    }

    /** Compact form used where only the core fields are known (e.g. re-wrapping after an import filter). */
    public CharacterCardData(
            String name,
            String description,
            String greeting,
            String systemPrompt,
            String sourceHash,
            String avatar,
            List<WorldBookEntry> worldEntries
    ) {
        this(name, description, "", "", greeting, systemPrompt, "", "", "", "",
                List.of(), sourceHash, avatar, worldEntries);
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

    public String getPersonality() {
        return personality;
    }

    public String getScenario() {
        return scenario;
    }

    public String getGreeting() {
        return greeting;
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
