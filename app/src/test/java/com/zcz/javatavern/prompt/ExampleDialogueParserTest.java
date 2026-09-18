package com.zcz.javatavern.prompt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class ExampleDialogueParserTest {

    private final ExampleDialogueParser parser = new ExampleDialogueParser();

    @Test
    public void parseExamples_nullOrBlank_returnsEmpty() {
        assertTrue(parser.parseExamples(null, "旅人", "薇拉").isEmpty());
        assertTrue(parser.parseExamples("   ", "旅人", "薇拉").isEmpty());
    }

    @Test
    public void parseExamples_singleTurnBlock_parsesOnePair() {
        String mesExample = "<START>\n{{user}}: 你好\n{{char}}: 欢迎光临";
        List<ExampleDialogueParser.ExampleExchange> examples =
                parser.parseExamples(mesExample, "旅人", "薇拉");

        assertEquals(1, examples.size());
        assertEquals("你好", examples.get(0).getUserMessage());
        assertEquals("欢迎光临", examples.get(0).getCharacterMessage());
    }

    /**
     * 回归测试：修复前 parseBlock 只保留块内最后一组 user/char 行，多轮示例会
     * 静默丢掉前面几轮——这是 SillyTavern 角色卡里很常见的写法。
     */
    @Test
    public void parseExamples_multiTurnBlock_keepsEveryTurn() {
        String mesExample = String.join("\n",
                "<START>",
                "{{user}}: 第一轮问题",
                "{{char}}: 第一轮回答",
                "{{user}}: 第二轮问题",
                "{{char}}: 第二轮回答",
                "{{user}}: 第三轮问题",
                "{{char}}: 第三轮回答"
        );

        List<ExampleDialogueParser.ExampleExchange> examples =
                parser.parseExamples(mesExample, "旅人", "薇拉");

        assertEquals(3, examples.size());
        assertEquals("第一轮问题", examples.get(0).getUserMessage());
        assertEquals("第一轮回答", examples.get(0).getCharacterMessage());
        assertEquals("第二轮问题", examples.get(1).getUserMessage());
        assertEquals("第二轮回答", examples.get(1).getCharacterMessage());
        assertEquals("第三轮问题", examples.get(2).getUserMessage());
        assertEquals("第三轮回答", examples.get(2).getCharacterMessage());
    }

    @Test
    public void parseExamples_multipleStartBlocks_parsesEachIndependently() {
        String mesExample = String.join("\n",
                "<START>",
                "{{user}}: 块一问题",
                "{{char}}: 块一回答",
                "<START>",
                "{{user}}: 块二问题",
                "{{char}}: 块二回答"
        );

        List<ExampleDialogueParser.ExampleExchange> examples =
                parser.parseExamples(mesExample, "旅人", "薇拉");

        assertEquals(2, examples.size());
        assertEquals("块一问题", examples.get(0).getUserMessage());
        assertEquals("块二问题", examples.get(1).getUserMessage());
    }

    @Test
    public void parseExamples_realCharacterNameInsteadOfMacro_alsoMatches() {
        String mesExample = "<START>\n旅人: 你好\n薇拉: 欢迎光临";
        List<ExampleDialogueParser.ExampleExchange> examples =
                parser.parseExamples(mesExample, "旅人", "薇拉");

        assertEquals(1, examples.size());
        assertEquals("你好", examples.get(0).getUserMessage());
    }

    @Test
    public void parseExamples_danglingUserLineWithoutReply_isDropped() {
        String mesExample = "<START>\n{{user}}: 没有回复的问题";
        List<ExampleDialogueParser.ExampleExchange> examples =
                parser.parseExamples(mesExample, "旅人", "薇拉");

        assertTrue(examples.isEmpty());
    }

    @Test
    public void formatExamplesForPrompt_emptyList_returnsEmptyString() {
        assertEquals("", parser.formatExamplesForPrompt(List.of(), "旅人", "薇拉"));
    }

    @Test
    public void formatExamplesForPrompt_includesAllExchangesInOrder() {
        List<ExampleDialogueParser.ExampleExchange> examples = List.of(
                new ExampleDialogueParser.ExampleExchange("问题一", "回答一"),
                new ExampleDialogueParser.ExampleExchange("问题二", "回答二")
        );

        String formatted = parser.formatExamplesForPrompt(examples, "旅人", "薇拉");

        assertTrue(formatted.contains("旅人: 问题一"));
        assertTrue(formatted.contains("薇拉: 回答一"));
        assertTrue(formatted.contains("旅人: 问题二"));
        assertTrue(formatted.contains("薇拉: 回答二"));
        assertTrue(formatted.indexOf("问题一") < formatted.indexOf("问题二"));
    }
}
