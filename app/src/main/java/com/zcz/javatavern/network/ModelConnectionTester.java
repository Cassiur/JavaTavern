package com.zcz.javatavern.network;

import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.ProviderCatalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ModelConnectionTester {
    private static final String ANTHROPIC_API_VERSION = "2023-06-01";

    public ConnectionTestResult test(ModelSettings settings) {
        long startedAt = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            boolean isAnthropic = ProviderCatalog.ANTHROPIC_ID.equals(settings.getProviderId());
            boolean isGoogle = ProviderCatalog.GOOGLE_ID.equals(settings.getProviderId());
            String endpoint = buildModelsEndpoint(settings.getBaseUrl());
            if (isGoogle) {
                // Gemini 没有 Bearer/x-api-key 这套，key 直接拼进查询串。
                endpoint += "?key=" + settings.getApiKey().trim();
            }
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/json");
            if (isAnthropic) {
                connection.setRequestProperty("x-api-key", settings.getApiKey());
                connection.setRequestProperty("anthropic-version", ANTHROPIC_API_VERSION);
            } else if (!isGoogle && !settings.getApiKey().trim().isEmpty()) {
                // Google 的 key 已经拼进 URL 查询串；OpenAI 兼容格式走 Bearer header。
                connection.setRequestProperty("Authorization", "Bearer " + settings.getApiKey());
            }
            int responseCode = connection.getResponseCode();
            long latency = System.currentTimeMillis() - startedAt;
            if (responseCode >= 200 && responseCode < 300) {
                return new ConnectionTestResult(true, "连接成功", latency);
            }
            if (responseCode == 401) {
                return new ConnectionTestResult(false, "API Key 无效或已过期", latency);
            }
            if (responseCode == 403) {
                return new ConnectionTestResult(false, "当前 Key 没有访问权限", latency);
            }
            if (responseCode == 404) {
                return new ConnectionTestResult(
                        false,
                        "服务可访问，但未提供标准模型列表接口",
                        latency
                );
            }
            return new ConnectionTestResult(
                    false,
                    "服务返回 HTTP " + responseCode + compactError(connection.getErrorStream()),
                    latency
            );
        } catch (Exception exception) {
            String message = exception.getMessage();
            return new ConnectionTestResult(
                    false,
                    message == null || message.trim().isEmpty() ? "无法连接到服务" : message,
                    System.currentTimeMillis() - startedAt
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    String buildModelsEndpoint(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            normalized = normalized.substring(
                    0,
                    normalized.length() - "/chat/completions".length()
            );
        }
        if (normalized.endsWith("/models")) {
            return normalized;
        }
        return normalized + "/models";
    }

    private String compactError(InputStream errorStream) throws IOException {
        if (errorStream == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                errorStream,
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null && result.length() < 180) {
                result.append(' ').append(line.trim());
            }
        }
        return result.toString();
    }
}
