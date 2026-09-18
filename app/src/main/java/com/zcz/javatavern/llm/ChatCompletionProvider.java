package com.zcz.javatavern.llm;

import com.zcz.javatavern.data.GenerationParams;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.function.Consumer;

/**
 * 统一的 Chat Completion API 提供者接口
 * 
 * 支持多种 LLM 服务商：OpenAI、Anthropic、Google、DeepSeek、OpenRouter 等
 */
public interface ChatCompletionProvider {
    
    /**
     * 发送聊天补全请求（流式）
     *
     * @param messages 消息列表
     * @param params 生成参数
     * @param callback 流式响应回调
     * @param connectionSink 连接建立后立即回传底层 {@link HttpURLConnection}，
     *        供调用方在另一个线程 {@code disconnect()} 实现取消；实现类必须在
     *        {@code openConnection()} 之后、开始阻塞读取之前调用一次
     * @throws IOException 网络或解析错误
     */
    void streamChatCompletion(
            List<ChatMessage> messages,
            GenerationParams params,
            StreamCallback callback,
            Consumer<HttpURLConnection> connectionSink
    ) throws IOException;
    
    /**
     * 获取提供者名称（用于日志和错误提示）
     */
    String getProviderName();
    
    /**
     * 流式响应回调
     */
    interface StreamCallback {
        /**
         * 接收到内容增量
         * @param delta 文本增量
         * @param isReasoning 是否为推理内容（DeepSeek R1 等）
         */
        void onContent(String delta, boolean isReasoning);
        
        /**
         * 流结束
         */
        void onComplete();
        
        /**
         * 发生错误
         */
        void onError(IOException error);
    }
    
    /**
     * 聊天消息
     */
    class ChatMessage {
        private final String role;  // system / user / assistant
        private final String content;
        
        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
        
        public String getRole() {
            return role;
        }
        
        public String getContent() {
            return content;
        }
    }
}
