package com.zcz.javatavern.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class GreetingSelectorTest {

    private final GreetingSelector selector = new GreetingSelector();

    @Test
    public void selectGreeting_indexMinusOne_alwaysReturnsPrimary() {
        assertEquals("primary", selector.selectGreeting(
                "primary", List.of("alt1", "alt2"), -1));
    }

    @Test
    public void selectGreeting_noAlternates_returnsPrimaryRegardlessOfIndex() {
        assertEquals("primary", selector.selectGreeting("primary", List.of(), -2));
        assertEquals("primary", selector.selectGreeting("primary", null, 0));
    }

    @Test
    public void selectGreeting_specificIndex_returnsThatAlternate() {
        List<String> alternates = List.of("alt1", "alt2", "alt3");
        assertEquals("alt1", selector.selectGreeting("primary", alternates, 0));
        assertEquals("alt3", selector.selectGreeting("primary", alternates, 2));
    }

    @Test
    public void selectGreeting_indexOutOfBounds_fallsBackToPrimary() {
        List<String> alternates = List.of("alt1");
        assertEquals("primary", selector.selectGreeting("primary", alternates, 5));
    }

    @Test
    public void selectGreeting_randomIndex_alwaysReturnsOneOfAllOptions() {
        List<String> alternates = List.of("alt1", "alt2");
        for (int i = 0; i < 50; i++) {
            String result = selector.selectGreeting("primary", alternates, -2);
            assertTrue(List.of("primary", "alt1", "alt2").contains(result));
        }
    }

    @Test
    public void getTotalGreetingCount_countsPrimaryPlusAlternates() {
        assertEquals(1, selector.getTotalGreetingCount(null));
        assertEquals(1, selector.getTotalGreetingCount(List.of()));
        assertEquals(3, selector.getTotalGreetingCount(List.of("a", "b")));
    }

    @Test
    public void getAllGreetings_primaryFirstThenAlternatesInOrder() {
        List<String> all = selector.getAllGreetings("primary", List.of("a", "b"));
        assertEquals(List.of("primary", "a", "b"), all);
    }

    @Test
    public void getAllGreetings_noAlternates_containsOnlyPrimary() {
        assertEquals(List.of("primary"), selector.getAllGreetings("primary", null));
    }
}
