package com.zcz.javatavern.model;

/**
 * 推理内容（Reasoning Content）
 * 
 * 用于存储 DeepSeek R1 等模型的思考过程
 */
public class ReasoningContent {
    private final long messageId;
    private final String content;
    private final long createdAt;
    
    public ReasoningContent(long messageId, String content, long createdAt) {
        this.messageId = messageId;
        this.content = content;
        this.createdAt = createdAt;
    }
    
    public long getMessageId() {
        return messageId;
    }
    
    public String getContent() {
        return content;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
}
