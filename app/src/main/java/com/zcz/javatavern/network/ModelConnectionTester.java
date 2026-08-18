package com.zcz.javatavern.network;

import com.zcz.javatavern.data.ModelSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ModelConnectionTester {
    public ConnectionTestResult test(ModelSettings settings) {
        long startedAt = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    buildModelsEndpoint(settings.getBaseUrl())
            ).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("Accept", "application/json");
            if (!settings.getApiKey().trim().isEmpty()) {
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
