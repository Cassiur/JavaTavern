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

    @Test
    public void generousBudgetKeepsEverythingUsable() {
        List<ChatMessage> messages = Arrays.asList(
                text(ChatMessage.Role.USER, "第一句", 1),
                text(ChatMessage.Role.ASSISTANT, "第二句", 2),
                text(ChatMessage.Role.USER, "第三句", 3)
        );

        List<ChatMessage> selected = ConversationWindow.selectWithinTokenBudget(messages, 10_000);

        assertEquals(3, selected.size());
        assertEquals("第一句", selected.get(0).getContent());
    }

    @Test
    public void tightBudgetKeepsOnlyTheMostRecentMessages() {
        List<ChatMessage> messages = Arrays.asList(
                text(ChatMessage.Role.USER, "这是一段很长的历史消息，用来消耗预算。", 1),
                text(ChatMessage.Role.ASSISTANT, "这是另一段同样很长的历史回复内容。", 2),
                text(ChatMessage.Role.USER, "最新", 3)
        );

        List<ChatMessage> selected = ConversationWindow.selectWithinTokenBudget(messages, 8);

        assertEquals(1, selected.size());
        assertEquals("最新", selected.get(0).getContent());
    }

    @Test
    public void alwaysKeepsAtLeastTheLatestMessage() {
        List<ChatMessage> messages = Arrays.asList(
                text(ChatMessage.Role.USER, "一条远超预算的超长消息内容", 1)
        );

        List<ChatMessage> selected = ConversationWindow.selectWithinTokenBudget(messages, 1);

        assertEquals(1, selected.size());
    }

    @Test
    public void zeroOrNegativeBudgetReturnsEmpty() {
        List<ChatMessage> messages = Arrays.asList(text(ChatMessage.Role.USER, "hi", 1));
        assertEquals(0, ConversationWindow.selectWithinTokenBudget(messages, 0).size());
        assertEquals(0, ConversationWindow.selectWithinTokenBudget(messages, -5).size());
        assertEquals(0, ConversationWindow.selectWithinTokenBudget(List.of(), 100).size());
    }

    @Test
    public void budgetSelectionSkipsCardsAndEmptyMessages() {
        List<ChatMessage> messages = Arrays.asList(
                card("agent card", 1),
                new ChatMessage(-1, ChatMessage.Role.USER, "   ", 2),
                text(ChatMessage.Role.USER, "有效内容", 3)
        );

        List<ChatMessage> selected = ConversationWindow.selectWithinTokenBudget(messages, 100);

        assertEquals(1, selected.size());
        assertEquals("有效内容", selected.get(0).getContent());
    }
}
