package com.zcz.javatavern.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.model.MemoryEntry;

import org.junit.Test;

import java.util.List;

public final class MemoryPromptFormatterTest {
    @Test
    public void formatsConfirmedEntriesAsBoundedList() {
        String prompt = MemoryPromptFormatter.format(List.of(
                new MemoryEntry("1", "用户喜欢简洁回答", 1),
                new MemoryEntry("2", "角色称呼用户为队长", 2)
        ), 100);

        assertEquals("- 用户喜欢简洁回答\n- 角色称呼用户为队长", prompt);
    }

    @Test
    public void respectsCharacterLimit() {
        String prompt = MemoryPromptFormatter.format(List.of(
                new MemoryEntry("1", "1234567890", 1)
        ), 6);

        assertTrue(prompt.length() <= 6);
    }

    @Test
    public void countsSeparatorsInsideCharacterLimit() {
        String prompt = MemoryPromptFormatter.format(List.of(
                new MemoryEntry("1", "一", 1),
                new MemoryEntry("2", "二二二", 2)
        ), 7);

        assertTrue(prompt.length() <= 7);
    }
}
