package com.zcz.javatavern.network;

import com.zcz.javatavern.model.ChatMessage;

import java.util.List;

/**
 * 启发式 Token 估算 —— 纯 Java，无 Android 依赖，便于 JVM 单元测试。
 *
 * <p>项目刻意不引入 tiktoken 之类的分词器依赖，因此这里用「字符类别加权」估算：
 * CJK 字符按 1 token/字，其余字符按 4 字符 1 token 折算，再叠加每条消息的
 * role/分隔开销与图片的固定开销。目的是**避免上下文超限直接报错**，不追求与
 * 具体模型的 tokenizer 完全一致；因此预算判断时保留安全余量。
 */
public final class TokenEstimator {

    /** 每条消息的结构开销（role 标记、分隔符等）。 */
    private static final int MESSAGE_OVERHEAD = 4;

    /** 单张图片的保守估算值。 */
    private static final int IMAGE_TOKENS = 800;

    private TokenEstimator() {
    }

    /** 估算一段文本的 token 数。 */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double tokens = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            tokens += isCjk(codePoint) ? 1.0 : 0.25;
            index += Character.charCount(codePoint);
        }
        return (int) Math.ceil(tokens);
    }

    /** 估算一组消息的总 token 数（含结构与附件开销）。 */
    public static int estimateMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage message : messages) {
            total += MESSAGE_OVERHEAD;
            total += estimate(message.getContent());
            if (message.hasImageAttachment()) {
                total += IMAGE_TOKENS;
            }
        }
        return total;
    }

    /**
     * CJK（中文、日文、韩文）及其全角标点按 1 字符 1 token 计，
     * 这些字符在主流分词器里通常也接近 1 token/字。
     */
    private static boolean isCjk(int codePoint) {
        return (codePoint >= 0x3000 && codePoint <= 0x303F)
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFF00 && codePoint <= 0xFFEF);
    }
}
