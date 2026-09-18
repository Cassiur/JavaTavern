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

/**
 * Anthropic Claude API 适配器
 * 
 * 支持 Claude 3.5 Sonnet、Claude 3 Opus 等模型
 * API 文档：https://docs.anthropic.com/claude/reference/messages-streaming
 */
public class AnthropicProvider implements ChatCompletionProvider {
    
    private static final String BASE_URL = "https://api.anthropic.com/v1/";
    private static final String API_VERSION = "2023-06-01";
    
    private final String apiKey;
    private final String model;
    
    public AnthropicProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }
    
    @Override
    public void streamChatCompletion(
            List<ChatMessage> messages,
            GenerationParams params,
            StreamCallback callback
    ) throws IOException {
        URL url = new URL(BASE_URL + "messages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-api-key", apiKey);
            conn.setRequestProperty("anthropic-version", API_VERSION);
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
        request.put("model", model);
        request.put("stream", true);
        
        // Anthropic 要求单独的 system 消息
        String systemMessage = null;
        JSONArray messagesArray = new JSONArray();
        
        for (ChatMessage msg : messages) {
            if ("system".equals(msg.getRole())) {
                systemMessage = msg.getContent();
            } else {
                JSONObject messageObj = new JSONObject();
                messageObj.put("role", msg.getRole());
                messageObj.put("content", msg.getContent());
                messagesArray.put(messageObj);
            }
        }
        
        if (systemMessage != null) {
            request.put("system", systemMessage);
        }
        request.put("messages", messagesArray);
        
        // 默认 max_tokens
        int maxTokens = params.getMaxTokens() != null ? params.getMaxTokens() : 4096;
        request.put("max_tokens", maxTokens);
        
        if (params.getTemperature() != null) {
            request.put("temperature", params.getTemperature());
        }
        if (params.getTopP() != null) {
            request.put("top_p", params.getTopP());
        }
        
        return request;
    }
    
    private void parseSSEStream(BufferedReader reader, StreamCallback callback) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                
                try {
                    JSONObject event = new JSONObject(data);
                    String eventType = event.optString("type");
                    
                    if ("content_block_delta".equals(eventType)) {
                        JSONObject delta = event.optJSONObject("delta");
                        if (delta != null) {
                            String text = delta.optString("text", null);
                            if (text != null && !text.isEmpty()) {
                                callback.onContent(text, false);
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
            return "Anthropic API Error";
        }
    }
    
    @Override
    public String getProviderName() {
        return "Anthropic";
    }
}
