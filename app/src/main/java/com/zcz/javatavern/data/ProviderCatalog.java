package com.zcz.javatavern.data;

import java.util.List;

public final class ProviderCatalog {
    public static final String CUSTOM_ID = "custom";
    /**
     * 非 OpenAI 兼容格式的原生 provider id——{@code OpenAiCompatibleClient}
     * 靠这两个 id 分流到 {@code AnthropicProvider}/{@code GoogleGeminiProvider}，
     * 而不是走默认的 {@code /chat/completions} 请求格式。
     */
    public static final String ANTHROPIC_ID = "anthropic";
    public static final String GOOGLE_ID = "google";
    private static final List<ProviderPreset> PRESETS = List.of(
            new ProviderPreset(
                    "openai",
                    "OpenAI",
                    "https://api.openai.com/v1",
                    "",
                    "填写账户可用的模型 ID"
            ),
            new ProviderPreset(
                    "deepseek",
                    "DeepSeek",
                    "https://api.deepseek.com",
                    "deepseek-v4-flash",
                    "例如 deepseek-v4-flash"
            ),
            new ProviderPreset(
                    "openrouter",
                    "OpenRouter",
                    "https://openrouter.ai/api/v1",
                    "",
                    "从 OpenRouter 模型页复制模型 ID"
            ),
            new ProviderPreset(
                    ANTHROPIC_ID,
                    "Anthropic",
                    "https://api.anthropic.com/v1",
                    "",
                    "例如 claude-sonnet-4-5"
            ),
            new ProviderPreset(
                    GOOGLE_ID,
                    "Google Gemini",
                    "https://generativelanguage.googleapis.com/v1beta",
                    "",
                    "例如 gemini-2.5-flash"
            ),
            new ProviderPreset(
                    CUSTOM_ID,
                    "其他兼容服务",
                    "",
                    "",
                    "填写服务提供方给出的模型 ID"
            )
    );

    private ProviderCatalog() {
    }

    public static List<ProviderPreset> getPresets() {
        return PRESETS;
    }

    public static ProviderPreset findById(String id) {
        for (ProviderPreset preset : PRESETS) {
            if (preset.getId().equals(id)) {
                return preset;
            }
        }
        return findById(CUSTOM_ID);
    }

    public static ProviderPreset matchBaseUrl(String baseUrl) {
        String normalized = normalize(baseUrl);
        for (ProviderPreset preset : PRESETS) {
            if (!preset.getBaseUrl().isEmpty()
                    && normalize(preset.getBaseUrl()).equals(normalized)) {
                return preset;
            }
        }
        return findById(CUSTOM_ID);
    }

    public static int indexOf(String id) {
        for (int index = 0; index < PRESETS.size(); index++) {
            if (PRESETS.get(index).getId().equals(id)) {
                return index;
            }
        }
        return PRESETS.size() - 1;
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
