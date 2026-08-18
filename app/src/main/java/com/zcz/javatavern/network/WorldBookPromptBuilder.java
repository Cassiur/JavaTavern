package com.zcz.javatavern.network;

import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.WorldBookEntry;

import java.util.List;
import java.util.Locale;

public final class WorldBookPromptBuilder {
    private static final int MAX_WORLD_BOOK_CHARS = 4_000;

    public String build(List<WorldBookEntry> entries, List<ChatMessage> conversation) {
        String searchableText = recentConversationText(conversation).toLowerCase(Locale.ROOT);
        StringBuilder prompt = new StringBuilder();
        for (WorldBookEntry entry : entries) {
            if (!entry.isEnabled() || !isActivated(entry, searchableText)) {
                continue;
            }
            String block = entry.getContent().trim();
            if (block.isEmpty() || prompt.length() + block.length() > MAX_WORLD_BOOK_CHARS) {
                continue;
            }
            if (prompt.length() > 0) {
                prompt.append("\n\n");
            }
            prompt.append(block);
        }
        return prompt.toString();
    }

    private boolean isActivated(WorldBookEntry entry, String searchableText) {
        if (entry.isConstant()) {
            return true;
        }
        for (String keyword : entry.getKeywords()) {
            if (searchableText.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String recentConversationText(List<ChatMessage> conversation) {
        StringBuilder text = new StringBuilder();
        int start = Math.max(0, conversation.size() - 12);
        for (int index = start; index < conversation.size(); index++) {
            ChatMessage message = conversation.get(index);
            if (message.getKind() == ChatMessage.Kind.TEXT) {
                text.append(message.getContent()).append('\n');
            }
        }
        return text.toString();
    }
}
