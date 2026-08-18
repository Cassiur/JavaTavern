package com.zcz.javatavern.network;

import static org.junit.Assert.assertEquals;

import com.zcz.javatavern.model.ChatMessage;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class ConversationWindowTest {
    @Test
    public void keepsRecentTextAndFiltersCards() {
        List<ChatMessage> messages = Arrays.asList(
                text(ChatMessage.Role.USER, "first", 1),
                card("agent", 2),
                text(ChatMessage.Role.ASSISTANT, "second", 3),
                text(ChatMessage.Role.USER, "third", 4)
        );

        List<ChatMessage> selected = ConversationWindow.selectRecentText(messages, 2);

        assertEquals(2, selected.size());
        assertEquals("second", selected.get(0).getContent());
        assertEquals("third", selected.get(1).getContent());
    }

    @Test
    public void zeroLimitReturnsEmptyWindow() {
        assertEquals(0, ConversationWindow.selectRecentText(
                Arrays.asList(text(ChatMessage.Role.USER, "hello", 1)),
                0
        ).size());
    }

    @Test
    public void keepsImageOnlyMessagesInModelContext() {
        ChatMessage imageMessage = new ChatMessage(
                -1,
                ChatMessage.Role.USER,
                ChatMessage.Kind.TEXT,
                "",
                "",
                1,
                "",
                "",
                ChatMessage.ActionState.NONE,
                "/tmp/image.jpg",
                "image/jpeg"
        );

        List<ChatMessage> selected = ConversationWindow.selectRecentText(
                Arrays.asList(imageMessage),
                5
        );

        assertEquals(1, selected.size());
        assertEquals("/tmp/image.jpg", selected.get(0).getAttachmentPath());
    }

    private ChatMessage text(ChatMessage.Role role, String content, long createdAt) {
        return new ChatMessage(-1, role, content, createdAt);
    }

    private ChatMessage card(String content, long createdAt) {
        return new ChatMessage(
                -1,
                ChatMessage.Role.ASSISTANT,
                ChatMessage.Kind.AGENT_CARD,
                "card",
                content,
                createdAt
        );
    }
}
