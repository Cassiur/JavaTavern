package com.zcz.javatavern.network;

import com.zcz.javatavern.model.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 从聊天记录里挑选要发给模型的上下文。
 *
 * <p>两种策略：
 * <ul>
 *   <li>{@link #selectRecentText(List, int)} —— 按消息条数截断（历史行为）。</li>
 *   <li>{@link #selectWithinTokenBudget(List, int)} —— 按 token 预算截断，避免
 *       长对话超出模型上下文后直接请求失败。</li>
 * </ul>
 */
public final class ConversationWindow {
    private ConversationWindow() {
    }

    /** 按条数挑选最近的可用文本消息（保留旧行为，便于对比与测试）。 */
    public static List<ChatMessage> selectRecentText(List<ChatMessage> messages, int limit) {
        if (limit <= 0 || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> selected = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0 && selected.size() < limit; index--) {
            ChatMessage message = messages.get(index);
            if (isUsable(message)) {
                selected.add(0, message);
            }
        }
        return selected;
    }

    /**
     * 在 token 预算内挑选最近的可用消息（从最新一条往前累积）。
     *
     * <p>至少会返回最新的一条可用消息 —— 即使它单独就超出预算，也不会返回空上下文，
     * 否则用户会看到「模型完全不知道上下文」这种更糟的结果。
     *
     * @param maxTokens 预算上限；调用方应已扣除 system prompt（角色卡、世界书、
     *                  长期记忆）的预留额度
     */
    public static List<ChatMessage> selectWithinTokenBudget(
            List<ChatMessage> messages,
            int maxTokens
    ) {
        if (maxTokens <= 0 || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> selected = new ArrayList<>();
        int used = 0;
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (!isUsable(message)) {
                continue;
            }
            int cost = TokenEstimator.estimateMessages(List.of(message));
            if (!selected.isEmpty() && used + cost > maxTokens) {
                break;
            }
            selected.add(0, message);
            used += cost;
        }
        return selected;
    }

    private static boolean isUsable(ChatMessage message) {
        return message.getKind() == ChatMessage.Kind.TEXT
                && (!message.getContent().trim().isEmpty() || message.hasImageAttachment());
    }
}
