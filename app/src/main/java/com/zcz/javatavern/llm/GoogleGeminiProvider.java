package com.zcz.javatavern.llm;

import com.zcz.javatavern.data.GenerationParams;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Google Gemini API 适配器
 *
 * 支持 Gemini 1.5 Pro、Gemini 1.5 Flash 等模型
 * API 文档：https://ai.google.dev/api/generate-content
 */
public class GoogleGeminiProvider implements ChatCompletionProvider {

    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public GoogleGeminiProvider(String baseUrl, String apiKey, String model) {
        String normalized = baseUrl == null || baseUrl.trim().isEmpty()
                ? DEFAULT_BASE_URL
                : baseUrl.trim();
        this.baseUrl = normalized.endsWith("/") ? normalized : normalized + "/";
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public void streamChatCompletion(
            List<ChatMessage> messages,
            GenerationParams params,
            StreamCallback callback,
            Consumer<HttpURLConnection> connectionSink
    ) throws IOException {
        String endpoint = String.format("models/%s:streamGenerateContent?key=%s&alt=sse", model, apiKey);
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        connectionSink.accept(conn);

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            
            JSONObject request;
            try {
                request = buildRequest(messages, params);
            } catch (org.json.JSONException e) {
                throw new IOException("Failed to build request JSON", e);
            }
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(request.toString().getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode + ": " + readError(conn));
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                parseSSEStream(reader, callback);
            }
            
            callback.onComplete();
            
        } catch (IOException e) {
            callback.onError(e);
            throw e;
        } finally {
            conn.disconnect();
        }
    }
    
    private JSONObject buildRequest(List<ChatMessage> messages, GenerationParams params) throws org.json.JSONException {
        JSONObject request = new JSONObject();
        
        // Gemini 格式：system instruction + contents
        String systemInstruction = null;
        JSONArray contents = new JSONArray();
        
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemInstruction = msg.getContent();
            } else {
                JSONObject content = new JSONObject();
                // Gemini 使用 "user" 和 "model"（而不是 "assistant"）
                String role = "assistant".equals(msg.getRole()) ? "model" : "user";
                content.put("role", role);
                
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", msg.getContent());
                parts.put(part);
                content.put("parts", parts);
                
                contents.put(content);
            }
        }
        
        if (systemInstruction != null) {
            JSONObject systemInstructionObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", systemInstruction);
            parts.put(part);
            systemInstructionObj.put("parts", parts);
            request.put("system_instruction", systemInstructionObj);
        }
        
        request.put("contents", contents);
        
        // Generation config
        JSONObject generationConfig = new JSONObject();
        if (params.getTemperature() != null) {
            generationConfig.put("temperature", params.getTemperature());
        }
        if (params.getTopP() != null) {
            generationConfig.put("topP", params.getTopP());
        }
        if (params.getMaxTokens() != null) {
            generationConfig.put("maxOutputTokens", params.getMaxTokens());
        }
        
        if (generationConfig.length() > 0) {
            request.put("generationConfig", generationConfig);
        }
        
        return request;
    }
    
    private void parseSSEStream(BufferedReader reader, StreamCallback callback) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                
                try {
                    JSONObject chunk = new JSONObject(data);
                    JSONArray candidates = chunk.optJSONArray("candidates");
                    
                    if (candidates != null && candidates.length() > 0) {
                        JSONObject candidate = candidates.getJSONObject(0);
                        JSONObject content = candidate.optJSONObject("content");
                        
                        if (content != null) {
                            JSONArray parts = content.optJSONArray("parts");
                            if (parts != null) {
                                for (int i = 0; i < parts.length(); i++) {
                                    JSONObject part = parts.getJSONObject(i);
                                    String text = part.optString("text", null);
                                    if (text != null && !text.isEmpty()) {
                                        callback.onContent(text, false);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }
        }
    }
    
    private String readError(HttpURLConnection conn) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "Google Gemini API Error";
        }
    }
    
    @Override
    public String getProviderName() {
        return "Google Gemini";
    }
}
