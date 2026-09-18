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
 * OpenAI 兼容格式的 API 适配器
 * 
 * 支持：
 * - OpenAI (api.openai.com)
 * - DeepSeek (api.deepseek.com) - 支持推理内容
 * - OpenRouter (openrouter.ai)
 * - 其他 OpenAI 兼容服务
 */
public class OpenAICompatibleProvider implements ChatCompletionProvider {
    
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    
    public OpenAICompatibleProvider(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.apiKey = apiKey;
        this.model = model;
    }
    
    @Override
    public void streamChatCompletion(
            List<ChatMessage> messages,
            GenerationParams params,
            StreamCallback callback
    ) throws IOException {
        URL url = new URL(baseUrl + "chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
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
        
        JSONArray messagesArray = new JSONArray();
        for (ChatMessage msg : messages) {
            JSONObject messageObj = new JSONObject();
            messageObj.put("role", msg.getRole());
            messageObj.put("content", msg.getContent());
            messagesArray.put(messageObj);
        }
        request.put("messages", messagesArray);
        
        if (params.getTemperature() != null) {
            request.put("temperature", params.getTemperature());
        }
        if (params.getTopP() != null) {
            request.put("top_p", params.getTopP());
        }
        if (params.getMaxTokens() != null) {
            request.put("max_tokens", params.getMaxTokens());
        }
        if (params.getFrequencyPenalty() != null) {
            request.put("frequency_penalty", params.getFrequencyPenalty());
        }
        if (params.getPresencePenalty() != null) {
            request.put("presence_penalty", params.getPresencePenalty());
        }
        
        return request;
    }
    
    private void parseSSEStream(BufferedReader reader, StreamCallback callback) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("data: ")) {
                String data = line.substring(6).trim();
                if (data.equals("[DONE]")) {
                    break;
                }
                
                try {
                    JSONObject chunk = new JSONObject(data);
                    JSONArray choices = chunk.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject delta = choice.optJSONObject("delta");
                        
                        if (delta != null) {
                            // 标准内容
                            String content = delta.optString("content", null);
                            if (content != null && !content.isEmpty()) {
                                callback.onContent(content, false);
                            }
                            
                            // DeepSeek R1 推理内容
                            String reasoning = delta.optString("reasoning_content", null);
                            if (reasoning != null && !reasoning.isEmpty()) {
                                callback.onContent(reasoning, true);
                            }
                        }
                    }
                } catch (Exception e) {
                    // 忽略解析错误，继续处理下一行
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
            return conn.getURL().toString();
        }
    }
    
    @Override
    public String getProviderName() {
        if (baseUrl.contains("deepseek")) {
            return "DeepSeek";
        } else if (baseUrl.contains("openrouter")) {
            return "OpenRouter";
        } else if (baseUrl.contains("openai")) {
            return "OpenAI";
        } else {
            return "OpenAI-Compatible";
        }
    }
}
