package com.zcz.javatavern.data;

import java.util.List;

public final class ProviderCatalog {
    public static final String CUSTOM_ID = "custom";
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
