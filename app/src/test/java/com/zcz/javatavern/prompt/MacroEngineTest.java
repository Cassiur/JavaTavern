package com.zcz.javatavern.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class MacroEngineTest {

    private final MacroEngine engine = new MacroEngine();

    @Test
    public void replaceMacros_substitutesCharAndUser() {
        String result = engine.replaceMacros("{{char}} 对 {{user}} 说你好", "薇拉", "旅人", 8000);
        assertEquals("薇拉 对 旅人 说你好", result);
    }

    @Test
    public void replaceMacros_substitutesContextMacros() {
        String result = engine.replaceMacros("max={{maxc}} min={{minc}}", "薇拉", "旅人", 8000);
        assertEquals("max=8000 min=4000", result);
    }

    @Test
    public void replaceMacros_nullOrEmptyText_returnsAsIs() {
        assertEquals(null, engine.replaceMacros(null, "薇拉", "旅人", 8000));
        assertEquals("", engine.replaceMacros("", "薇拉", "旅人", 8000));
    }

    @Test
    public void replaceRandomMacros_picksOneOfTheGivenOptions() {
        String result = engine.replaceMacros("{{random::a::b::c}}", "薇拉", "旅人", 8000);
        assertTrue(List.of("a", "b", "c").contains(result));
    }

    @Test
    public void replaceRandomMacros_singleOption_alwaysPicksIt() {
        String result = engine.replaceMacros("{{random::only}}", "薇拉", "旅人", 8000);
        assertEquals("only", result);
    }

    @Test
    public void replaceRollMacros_totalIsWithinDiceRange() {
        for (int i = 0; i < 50; i++) {
            String result = engine.replaceMacros("{{roll:2d6}}", "薇拉", "旅人", 8000);
            int total = Integer.parseInt(result);
            assertTrue("roll total must be between 2 and 12, was " + total,
                    total >= 2 && total <= 12);
        }
    }

    @Test
    public void containsMacros_detectsBraces() {
        assertTrue(engine.containsMacros("hello {{char}}"));
        assertFalse(engine.containsMacros("hello world"));
        assertFalse(engine.containsMacros(""));
        assertFalse(engine.containsMacros(null));
    }

    @Test
    public void replaceMacros_overloadWithoutModels_matchesStringOverload() {
        com.zcz.javatavern.model.CharacterProfile character = new com.zcz.javatavern.model.CharacterProfile(
                "id", "薇拉", "desc", "hi", 0);
        com.zcz.javatavern.model.Persona persona =
                new com.zcz.javatavern.model.Persona("pid", "旅人", "", false, "");

        String viaModels = engine.replaceMacros("{{char}}/{{user}}", character, persona, 8000);
        String viaStrings = engine.replaceMacros("{{char}}/{{user}}", "薇拉", "旅人", 8000);
        assertEquals(viaStrings, viaModels);
    }
}
