package com.zcz.javatavern.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.WorldBookEntry;

import org.junit.Test;

import java.util.List;

public final class WorldBookPromptBuilderTest {
    private final WorldBookPromptBuilder builder = new WorldBookPromptBuilder();

    @Test
    public void activatesConstantAndKeywordEntriesOnly() {
        List<WorldBookEntry> entries = List.of(
                new WorldBookEntry(List.of("星港"), "七座环形站", true, false),
                new WorldBookEntry(List.of("沙漠"), "沙海规则", true, false),
                new WorldBookEntry(List.of(), "始终记录航线", true, true),
                new WorldBookEntry(List.of("星港"), "已禁用", false, false)
        );
        List<ChatMessage> conversation = List.of(new ChatMessage(
                1,
                ChatMessage.Role.USER,
                "我们抵达星港了吗？",
                1
        ));

        String prompt = builder.build(entries, conversation);

        assertTrue(prompt.contains("七座环形站"));
        assertTrue(prompt.contains("始终记录航线"));
        assertFalse(prompt.contains("沙海规则"));
        assertFalse(prompt.contains("已禁用"));
    }
}
