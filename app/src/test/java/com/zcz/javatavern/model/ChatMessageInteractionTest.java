package com.zcz.javatavern.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ChatMessageInteractionTest {
    @Test
    public void legacyConstructorKeepsInteractionMetadataEmpty() {
        ChatMessage message = new ChatMessage(1, ChatMessage.Role.USER, "你好", 10);

        assertFalse(message.hasReply());
        assertFalse(message.hasReaction());
        assertEquals(-1, message.getReplyToMessageId());
    }

    @Test
    public void fullConstructorExposesReplyAndReaction() {
        ChatMessage message = new ChatMessage(
                2,
                ChatMessage.Role.USER,
                ChatMessage.Kind.TEXT,
                "",
                "继续说",
                20,
                "",
                "",
                ChatMessage.ActionState.NONE,
                "",
                "",
                1,
                "角色：上一句话",
                "👍"
        );

        assertTrue(message.hasReply());
        assertTrue(message.hasReaction());
        assertEquals("角色：上一句话", message.getReplyPreview());
        assertEquals("👍", message.getReaction());
    }
}
