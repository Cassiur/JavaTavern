package com.zcz.javatavern.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.model.CharacterCardData;
import com.zcz.javatavern.model.WorldBookEntry;

import org.junit.Test;

import java.util.List;

public final class CharacterCardParserTest {

    private final CharacterCardParser parser = new CharacterCardParser();

    @Test
    public void parsesAdvancedWorldEntryFields() throws Exception {
        String json = "{"
                + "\"data\":{"
                + "\"name\":\"测试角色\","
                + "\"description\":\"desc\","
                + "\"first_mes\":\"hi\","
                + "\"character_book\":{\"entries\":["
                + "{\"keys\":[\"猫\"],\"content\":\"猫咪内容\","
                + "\"position\":\"before_char\",\"insertion_order\":42,"
                + "\"priority\":7,\"depth\":3,\"probability\":55,"
                + "\"exclude_recursion\":true,\"prevent_recursion\":true},"
                + "{\"keys\":[\"狗\"],\"content\":\"狗狗内容\","
                + "\"position\":1,\"insertion_order\":\"10\","
                + "\"priority\":\"5\",\"depth\":\"2\",\"probability\":\"100\"}"
                + "]}}}";

        CharacterCardData card = parser.parse(json);
        List<WorldBookEntry> entries = card.getWorldEntries();
        assertEquals(2, entries.size());

        WorldBookEntry first = entries.get(0);
        assertEquals(WorldBookEntry.POSITION_BEFORE_CHAR, first.getPosition());
        assertEquals(42, first.getOrder());
        assertEquals(7, first.getPriority());
        assertEquals(3, first.getDepth());
        assertEquals(55, first.getProbability());
        assertTrue(first.isExcludeRecursion());
        assertTrue(first.isPreventRecursion());

        WorldBookEntry second = entries.get(1);
        assertEquals(WorldBookEntry.POSITION_AFTER_CHAR, second.getPosition());
        assertEquals(10, second.getOrder());
        assertEquals(5, second.getPriority());
        assertEquals(2, second.getDepth());
        assertEquals(100, second.getProbability());
        assertFalse(second.isExcludeRecursion());
        assertFalse(second.isPreventRecursion());
    }

    @Test
    public void defaultsAppliedWhenAdvancedFieldsMissing() throws Exception {
        String json = "{"
                + "\"data\":{"
                + "\"name\":\"测试角色\","
                + "\"character_book\":{\"entries\":["
                + "{\"keys\":[\"猫\"],\"content\":\"猫咪内容\"}"
                + "]}}}";

        CharacterCardData card = parser.parse(json);
        WorldBookEntry entry = card.getWorldEntries().get(0);

        assertEquals(WorldBookEntry.POSITION_BEFORE_CHAR, entry.getPosition());
        assertEquals(WorldBookEntry.DEFAULT_ORDER, entry.getOrder());
        assertEquals(0, entry.getPriority());
        assertEquals(WorldBookEntry.DEFAULT_DEPTH, entry.getDepth());
        assertEquals(WorldBookEntry.DEFAULT_PROBABILITY, entry.getProbability());
        assertFalse(entry.isExcludeRecursion());
        assertFalse(entry.isPreventRecursion());
    }
}
