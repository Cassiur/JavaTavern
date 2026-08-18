package com.zcz.javatavern.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.model.AgentCard;

import org.junit.Test;

public final class LocalAgentRouterTest {
    private final LocalAgentRouter router = new LocalAgentRouter();

    @Test
    public void unknownMessageDoesNotInterceptChat() {
        assertNull(router.route("普通聊天消息"));
    }

    @Test
    public void planCommandCreatesStructuredCard() {
        AgentCard card = router.route("/plan 完成流式对话");

        assertEquals("计划 Agent", card.getTitle());
        assertTrue(card.getBody().contains("完成流式对话"));
        assertTrue(card.getBody().contains("最小可运行结果"));
    }

    @Test
    public void statusCommandReturnsReadOnlyCapabilityCard() {
        AgentCard card = router.route("/status");

        assertEquals("只读检查", card.getBadge());
        assertTrue(card.getBody().contains("SSE 流式与取消"));
        assertTrue(card.getBody().contains("图片多模态输入"));
    }

    @Test
    public void clearCommandRequiresExplicitConfirmation() {
        AgentCard card = router.route("/clear");

        assertTrue(card.requiresConfirmation());
        assertEquals("clear_conversation", card.getActionType());
        assertTrue(card.getBody().contains("无法在应用内撤销"));
    }
}
