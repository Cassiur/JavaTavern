package com.zcz.javatavern.network;

import com.zcz.javatavern.model.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConversationWindow {
    private ConversationWindow() {
    }

    public static List<ChatMessage> selectRecentText(List<ChatMessage> messages, int limit) {
        if (limit <= 0 || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMessage> selected = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0 && selected.size() < limit; index--) {
            ChatMessage message = messages.get(index);
            if (message.getKind() == ChatMessage.Kind.TEXT
                    && (!message.getContent().trim().isEmpty() || message.hasImageAttachment())) {
                selected.add(0, message);
            }
        }
        return selected;
    }
}
