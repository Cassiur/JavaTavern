package com.zcz.javatavern.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SseEventParserTest {
    @Test
    public void parsesOpenAiCompatibleDelta() throws Exception {
        SseEventParser.Event event = SseEventParser.parse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}"
        );

        assertFalse(event.isDone());
        assertEquals("你", event.getDelta());
    }

    @Test
    public void recognizesDoneEvent() throws Exception {
        SseEventParser.Event event = SseEventParser.parse("data: [DONE]");

        assertTrue(event.isDone());
        assertEquals("", event.getDelta());
    }

    @Test
    public void ignoresUsageOnlyEvent() throws Exception {
        SseEventParser.Event event = SseEventParser.parse(
                "data: {\"choices\":[],\"usage\":{\"total_tokens\":12}}"
        );

        assertFalse(event.isDone());
        assertEquals("", event.getDelta());
    }

    @Test
    public void skipsMalformedJsonInsteadOfThrowing() {
        SseEventParser.Event event = SseEventParser.parse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"trunc"
        );

        assertFalse(event.isDone());
        assertEquals("", event.getDelta());
    }

    @Test
    public void skipsNonJsonCommentLikeDataPayload() {
        SseEventParser.Event event = SseEventParser.parse("data: keep-alive");

        assertFalse(event.isDone());
        assertEquals("", event.getDelta());
    }

    @Test
    public void parsesDeepSeekReasoningContentDelta() {
        SseEventParser.Event event = SseEventParser.parse(
                "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"让我想想\"}}]}"
        );

        assertFalse(event.isDone());
        assertEquals("", event.getDelta());
        assertEquals("让我想想", event.getReasoningDelta());
    }

    @Test
    public void parsesContentAndReasoningInSameDelta() {
        SseEventParser.Event event = SseEventParser.parse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"答案\",\"reasoning_content\":\"思考\"}}]}"
        );

        assertEquals("答案", event.getDelta());
        assertEquals("思考", event.getReasoningDelta());
    }

    @Test
    public void plainDeltaWithoutReasoning_returnsEmptyReasoningDelta() {
        SseEventParser.Event event = SseEventParser.parse(
                "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}"
        );

        assertEquals("", event.getReasoningDelta());
    }
}
