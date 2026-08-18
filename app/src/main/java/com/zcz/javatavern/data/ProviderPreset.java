package com.zcz.javatavern.data;

public final class ProviderPreset {
    private final String id;
    private final String displayName;
    private final String baseUrl;
    private final String defaultModel;
    private final String modelHint;

    public ProviderPreset(
            String id,
            String displayName,
            String baseUrl,
            String defaultModel,
            String modelHint
    ) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.modelHint = modelHint;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public String getModelHint() {
        return modelHint;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
