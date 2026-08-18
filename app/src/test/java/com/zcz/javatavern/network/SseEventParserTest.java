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
}
