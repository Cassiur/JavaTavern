package com.zcz.javatavern.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.model.ChatMessage;

import org.junit.Test;

import java.util.List;

public final class TokenEstimatorTest {

    @Test
    public void asciiIsEstimatedAtFourCharsPerToken() {
        assertEquals(1, TokenEstimator.estimate("abcd"));
        assertEquals(3, TokenEstimator.estimate("abcdefghijkl"));
    }

    @Test
    public void cjkIsEstimatedAtOneTokenPerChar() {
        assertEquals(4, TokenEstimator.estimate("你好世界"));
    }

    @Test
    public void mixedContentSumsBothCategories() {
        assertEquals(3, TokenEstimator.estimate("你好abcd"));
    }

    @Test
    public void emptyAndNullEstimateZero() {
        assertEquals(0, TokenEstimator.estimate(""));
        assertEquals(0, TokenEstimator.estimate(null));
        assertEquals(0, TokenEstimator.estimateMessages(List.of()));
        assertEquals(0, TokenEstimator.estimateMessages(null));
    }

    @Test
    public void messagesIncludePerMessageOverhead() {
        ChatMessage message = new ChatMessage(1L, ChatMessage.Role.USER, "你好", 0L);
        assertEquals(6, TokenEstimator.estimateMessages(List.of(message)));
    }

    @Test
    public void imageAttachmentAddsFixedCost() {
        ChatMessage text = new ChatMessage(1L, ChatMessage.Role.USER, "看这个", 0L);
        ChatMessage withImage = new ChatMessage(
                2L,
                ChatMessage.Role.USER,
                ChatMessage.Kind.TEXT,
                "",
                "看这个",
                0L,
                "",
                "",
                ChatMessage.ActionState.NONE,
                "/tmp/photo.jpg",
                "image/jpeg"
        );
        assertTrue(TokenEstimator.estimateMessages(List.of(withImage))
                > TokenEstimator.estimateMessages(List.of(text)));
    }

    @Test
    public void longerTextAlwaysEstimatesAtLeastAsMuch() {
        String shortText = "hello";
        String longText = "hello world, this is a much longer sentence.";
        assertTrue(TokenEstimator.estimate(longText) > TokenEstimator.estimate(shortText));
    }
}
