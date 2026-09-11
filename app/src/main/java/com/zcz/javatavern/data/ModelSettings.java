package com.zcz.javatavern.data;

import androidx.annotation.NonNull;

public final class ModelSettings {
    /**
     * 默认上下文预算（输入侧 token 上限）。本地截断参数，不会发给服务端；
     * 取一个对主流模型都安全的保守值，留出 system prompt 与输出的余量。
     */
    public static final int DEFAULT_CONTEXT_TOKENS = 8000;

    private final String providerId;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final GenerationParams generationParams;
    private final int contextTokens;

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
        this(providerId, baseUrl, model, apiKey, generationParams, DEFAULT_CONTEXT_TOKENS);
    }

    public ModelSettings(
            String providerId,
            String baseUrl,
            String model,
            String apiKey,
            GenerationParams generationParams,
            int contextTokens
    ) {
        this.providerId = providerId;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.generationParams = generationParams == null
                ? GenerationParams.EMPTY
                : generationParams;
        this.contextTokens = contextTokens <= 0 ? DEFAULT_CONTEXT_TOKENS : contextTokens;
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

    /** 上下文预算（输入侧 token 上限），本地截断使用。 */
    public int getContextTokens() {
        return contextTokens;
    }

    public boolean isRemoteConfigured() {
        return !baseUrl.trim().isEmpty() && !model.trim().isEmpty();
    }
}
