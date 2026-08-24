package com.zcz.javatavern.data;

import androidx.annotation.Nullable;

/**
 * 采样参数集合。SillyTavern 风格：null 表示不发送该参数，由服务端使用默认值。
 */
public final class GenerationParams {
    public static final GenerationParams EMPTY = new GenerationParams(
            null, null, null, null, null);

    @Nullable
    private final Double temperature;
    @Nullable
    private final Double topP;
    @Nullable
    private final Integer maxTokens;
    @Nullable
    private final Double frequencyPenalty;
    @Nullable
    private final Double presencePenalty;

    public GenerationParams(
            @Nullable Double temperature,
            @Nullable Double topP,
            @Nullable Integer maxTokens,
            @Nullable Double frequencyPenalty,
            @Nullable Double presencePenalty
    ) {
        this.temperature = temperature;
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.frequencyPenalty = frequencyPenalty;
        this.presencePenalty = presencePenalty;
    }

    @Nullable
    public Double getTemperature() {
        return temperature;
    }

    @Nullable
    public Double getTopP() {
        return topP;
    }

    @Nullable
    public Integer getMaxTokens() {
        return maxTokens;
    }

    @Nullable
    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    @Nullable
    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public boolean isEmpty() {
        return temperature == null
                && topP == null
                && maxTokens == null
                && frequencyPenalty == null
                && presencePenalty == null;
    }
}
