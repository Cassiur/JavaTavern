package com.zcz.javatavern.llm;

/**
 * API 提供者工厂
 * 
 * 根据 API 类型创建对应的 ChatCompletionProvider
 */
public class ProviderFactory {
    
    public enum ProviderType {
        OPENAI,
        ANTHROPIC,
        GOOGLE_GEMINI,
        DEEPSEEK,
        OPENROUTER,
        OPENAI_COMPATIBLE  // 自定义 OpenAI 兼容端点
    }
    
    /**
     * 创建 Provider
     * 
     * @param type 提供者类型
     * @param baseUrl API 基础 URL（仅 OPENAI_COMPATIBLE 需要）
     * @param apiKey API 密钥
     * @param model 模型名称
     * @return ChatCompletionProvider 实例
     */
    public static ChatCompletionProvider createProvider(
            ProviderType type,
            String baseUrl,
            String apiKey,
            String model
    ) {
        switch (type) {
            case OPENAI:
                return new OpenAICompatibleProvider(
                        "https://api.openai.com/v1/",
                        apiKey,
                        model
                );
                
            case ANTHROPIC:
                return new AnthropicProvider(apiKey, model);
                
            case GOOGLE_GEMINI:
                return new GoogleGeminiProvider(apiKey, model);
                
            case DEEPSEEK:
                return new OpenAICompatibleProvider(
                        "https://api.deepseek.com/",
                        apiKey,
                        model
                );
                
            case OPENROUTER:
                return new OpenAICompatibleProvider(
                        "https://openrouter.ai/api/v1/",
                        apiKey,
                        model
                );
                
            case OPENAI_COMPATIBLE:
                if (baseUrl == null || baseUrl.isEmpty()) {
                    throw new IllegalArgumentException("baseUrl is required for OPENAI_COMPATIBLE");
                }
                return new OpenAICompatibleProvider(baseUrl, apiKey, model);
                
            default:
                throw new IllegalArgumentException("Unknown provider type: " + type);
        }
    }
    
    /**
     * 根据字符串名称创建 Provider（用于从配置读取）
     */
    public static ChatCompletionProvider createProvider(
            String typeName,
            String baseUrl,
            String apiKey,
            String model
    ) {
        ProviderType type;
        try {
            type = ProviderType.valueOf(typeName.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown provider: " + typeName);
        }
        return createProvider(type, baseUrl, apiKey, model);
    }
}
