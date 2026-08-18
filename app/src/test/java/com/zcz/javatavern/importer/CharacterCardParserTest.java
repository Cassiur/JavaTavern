package com.zcz.javatavern.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.model.CharacterCardData;

import org.json.JSONException;
import org.junit.Test;

public final class CharacterCardParserTest {
    private final CharacterCardParser parser = new CharacterCardParser();

    @Test
    public void parsesSillyTavernV2CardAndWorldBook() throws JSONException {
        String json = """
                {
                  "spec": "chara_card_v2",
                  "data": {
                    "name": "星港领航员",
                    "description": "负责引导旅人穿过星港。",
                    "personality": "冷静、可靠",
                    "scenario": "深夜星港",
                    "first_mes": "航线已经准备好了。",
                    "character_book": {
                      "entries": [
                        {"keys":["星港","航线"],"content":"星港由七座环形站组成。","enabled":true},
                        {"keys":[],"content":"始终保持导航日志。","constant":true}
                      ]
                    }
                  }
                }
                """;

        CharacterCardData card = parser.parse(json);

        assertEquals("星港领航员", card.getName());
        assertEquals("航线已经准备好了。", card.getGreeting());
        assertTrue(card.getSystemPrompt().contains("冷静、可靠"));
        assertEquals(2, card.getWorldEntries().size());
        assertEquals("星港", card.getWorldEntries().get(0).getKeywords().get(0));
        assertTrue(card.getWorldEntries().get(1).isConstant());
        assertFalse(card.getSourceHash().isEmpty());
    }

    @Test(expected = JSONException.class)
    public void rejectsCardWithoutName() throws JSONException {
        parser.parse("{\"data\":{\"description\":\"missing name\"}}");
    }
}
