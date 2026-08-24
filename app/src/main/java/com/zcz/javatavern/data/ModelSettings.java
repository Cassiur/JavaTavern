package com.zcz.javatavern.data;

import androidx.annotation.NonNull;

public final class ModelSettings {
    private final String providerId;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final GenerationParams generationParams;

    public ModelSettings(String baseUrl, String model, String apiKey) {
        this(ProviderCatalog.matchBaseUrl(baseUrl).getId(), baseUrl, model, apiKey);
    }

    public ModelSettings(String providerId, String baseUrl, String model, String apiKey) {
        this(providerId, baseUrl, model, apiKey, GenerationParams.EMPTY);
    }

    public ModelSettings(
            String providerId,
            String baseUrl,
            String model,
            String apiKey,
            GenerationParams generationParams
    ) {
        this.providerId = providerId;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.generationParams = generationParams == null
                ? GenerationParams.EMPTY
                : generationParams;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    @NonNull
    public GenerationParams getGenerationParams() {
        return generationParams;
    }

    public boolean isRemoteConfigured() {
        return !baseUrl.trim().isEmpty() && !model.trim().isEmpty();
    }
}
