package com.zcz.javatavern.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 重 roll 多版本在模型层的语义：默认单版本、序号与总数被夹取到合法区间、
 * 翻页方向判断，以及各 with* 副本必须保留版本信息。
 */
public class ChatMessageVersionTest {

    private static ChatMessage text(String content) {
        return new ChatMessage(7L, ChatMessage.Role.ASSISTANT, content, 1000L);
    }

    @Test
    public void newMessageIsASingleVersion() {
        ChatMessage message = text("你好");

        assertEquals(1, message.getActiveVersion());
        assertEquals(1, message.getVersionCount());
        assertFalse(message.hasVersions());
        assertFalse(message.hasPreviousVersion());
        assertFalse(message.hasNextVersion());
    }

    @Test
    public void withVersionInfoExposesSwiping() {
        ChatMessage message = text("第二版").withVersionInfo(2, 3);

        assertEquals(2, message.getActiveVersion());
        assertEquals(3, message.getVersionCount());
        assertTrue(message.hasVersions());
        assertTrue(message.hasPreviousVersion());
        assertTrue(message.hasNextVersion());
    }

    @Test
    public void firstVersionCannotGoBack() {
        ChatMessage message = text("第一版").withVersionInfo(1, 3);

        assertFalse(message.hasPreviousVersion());
        assertTrue(message.hasNextVersion());
    }

    @Test
    public void lastVersionCannotGoForward() {
        ChatMessage message = text("第三版").withVersionInfo(3, 3);

        assertTrue(message.hasPreviousVersion());
        assertFalse(message.hasNextVersion());
    }

    @Test
    public void versionNumbersAreClampedToValidRange() {
        assertEquals(1, text("x").withVersionInfo(0, 3).getActiveVersion());
        assertEquals(3, text("x").withVersionInfo(9, 3).getActiveVersion());
        assertEquals(1, text("x").withVersionInfo(-5, 3).getActiveVersion());
    }

    @Test
    public void versionCountIsNeverBelowOne() {
        ChatMessage message = text("x").withVersionInfo(1, 0);

        assertEquals(1, message.getVersionCount());
        assertEquals(1, message.getActiveVersion());
    }

    @Test
    public void withContentKeepsVersionInfo() {
        ChatMessage message = text("原文").withVersionInfo(2, 2);
        ChatMessage edited = message.withContent("改过的第二版");

        assertEquals("改过的第二版", edited.getContent());
        assertEquals(2, edited.getActiveVersion());
        assertEquals(2, edited.getVersionCount());
    }

    @Test
    public void withVersionInfoKeepsContentAndSpeaker() {
        ChatMessage message = new ChatMessage(
                5L, ChatMessage.Role.ASSISTANT, "内容", 20L, "薇拉");
        ChatMessage second = message.withVersionInfo(2, 2);

        assertEquals("内容", second.getContent());
        assertEquals("薇拉", second.getSpeakerName());
        assertEquals(5L, second.getId());
        assertEquals(20L, second.getCreatedAt());
    }

    @Test
    public void shorterConstructorsDefaultToSingleVersion() {
        ChatMessage rich = new ChatMessage(
                1L,
                ChatMessage.Role.ASSISTANT,
                ChatMessage.Kind.AGENT_CARD,
                "标题",
                "正文",
                10L,
                "token",
                "type",
                ChatMessage.ActionState.PENDING,
                "",
                "",
                2L,
                "引用",
                "👍",
                "薇拉"
        );

        assertEquals(1, rich.getActiveVersion());
        assertEquals(1, rich.getVersionCount());
        assertFalse(rich.hasVersions());
    }
}
