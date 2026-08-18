package com.zcz.javatavern.data;

public final class ModelSettings {
    private final String providerId;
    private final String baseUrl;
    private final String model;
    private final String apiKey;

    public ModelSettings(String baseUrl, String model, String apiKey) {
        this(ProviderCatalog.matchBaseUrl(baseUrl).getId(), baseUrl, model, apiKey);
    }

    public ModelSettings(String providerId, String baseUrl, String model, String apiKey) {
        this.providerId = providerId;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
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

    public boolean isRemoteConfigured() {
        return !baseUrl.trim().isEmpty() && !model.trim().isEmpty();
    }
}
