package com.zcz.javatavern.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class MarkdownParserTest {

    private static String plainText(String markdown) {
        StringBuilder builder = new StringBuilder();
        for (MarkdownParser.Block block : MarkdownParser.parse(markdown)) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (block.isCodeBlock()) {
                builder.append(block.getCode());
                continue;
            }
            for (MarkdownParser.Inline inline : block.getInlines()) {
                builder.append(inline.getText());
            }
        }
        return builder.toString();
    }

    private static MarkdownParser.Inline inlineWithText(String markdown, String text) {
        for (MarkdownParser.Block block : MarkdownParser.parse(markdown)) {
            for (MarkdownParser.Inline inline : block.getInlines()) {
                if (inline.getText().equals(text)) {
                    return inline;
                }
            }
        }
        return null;
    }

    private static MarkdownParser.Block firstBlock(String markdown) {
        List<MarkdownParser.Block> blocks = MarkdownParser.parse(markdown);
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    @Test
    public void plainTextIsReturnedUnchanged() {
        assertEquals("hello world", plainText("hello world"));
    }

    @Test
    public void boldMarkersAreRemovedAndStyled() {
        MarkdownParser.Inline inline = inlineWithText("**bold**", "bold");
        assertNotNull(inline);
        assertTrue(inline.isBold());
        assertFalse(inline.isItalic());
    }

    @Test
    public void italicMarkersAreRemovedAndStyled() {
        MarkdownParser.Inline inline = inlineWithText("*她别过头去*", "她别过头去");
        assertNotNull(inline);
        assertTrue(inline.isItalic());
        assertFalse(inline.isBold());
    }

    @Test
    public void multipleItalicSpansInOneLine() {
        String markdown = "*拿起杯子* 她顿了顿 *又放下*";
        assertEquals("拿起杯子 她顿了顿 又放下", plainText(markdown));
        assertTrue(inlineWithText(markdown, "拿起杯子").isItalic());
        assertTrue(inlineWithText(markdown, "又放下").isItalic());
        MarkdownParser.Inline plain = inlineWithText(markdown, " 她顿了顿 ");
        assertNotNull(plain);
        assertTrue(plain.isPlain());
    }

    @Test
    public void inlineCodeIsStyled() {
        MarkdownParser.Inline inline = inlineWithText("run `gradlew build` now", "gradlew build");
        assertNotNull(inline);
        assertTrue(inline.isCode());
    }

    @Test
    public void strikethroughIsStyled() {
        MarkdownParser.Inline inline = inlineWithText("~~gone~~", "gone");
        assertNotNull(inline);
        assertTrue(inline.isStrikethrough());
    }

    @Test
    public void nestedItalicInsideBold() {
        String markdown = "**粗体里的 *斜体* 部分**";
        assertEquals("粗体里的 斜体 部分", plainText(markdown));
        MarkdownParser.Inline inner = inlineWithText(markdown, "斜体");
        assertNotNull(inner);
        assertTrue(inner.isBold());
        assertTrue(inner.isItalic());
        MarkdownParser.Inline outer = inlineWithText(markdown, "粗体里的 ");
        assertNotNull(outer);
        assertTrue(outer.isBold());
        assertFalse(outer.isItalic());
    }

    @Test
    public void unclosedMarkerStaysLiteral() {
        String markdown = "an *unclosed marker here";
        assertEquals(markdown, plainText(markdown));
        MarkdownParser.Inline inline = inlineWithText(markdown, markdown);
        assertNotNull(inline);
        assertTrue(inline.isPlain());
    }

    @Test
    public void unmatchedDoubleAsteriskStaysLiteral() {
        String markdown = "2 ** 3 is not markdown";
        assertEquals(markdown, plainText(markdown));
    }

    @Test
    public void boldAndItalicDoNotInterfere() {
        assertEquals("bold", plainText("**bold**"));
        assertEquals("italic", plainText("*italic*"));
    }

    @Test
    public void headingIsDetectedWithLevel() {
        MarkdownParser.Block block = firstBlock("## 第二章");
        assertNotNull(block);
        assertEquals(MarkdownParser.Block.Kind.HEADING, block.getKind());
        assertEquals(2, block.getLevel());
        assertEquals("第二章", plainText("## 第二章"));
    }

    @Test
    public void quoteIsDetected() {
        MarkdownParser.Block block = firstBlock("> 她说得很轻");
        assertNotNull(block);
        assertEquals(MarkdownParser.Block.Kind.QUOTE, block.getKind());
        assertEquals("她说得很轻", plainText("> 她说得很轻"));
    }

    @Test
    public void bulletListIsDetected() {
        MarkdownParser.Block block = firstBlock("- 第一项");
        assertNotNull(block);
        assertEquals(MarkdownParser.Block.Kind.BULLET_ITEM, block.getKind());
        assertEquals("第一项", plainText("- 第一项"));
    }

    @Test
    public void orderedListIsDetected() {
        MarkdownParser.Block block = firstBlock("1. 第一步");
        assertNotNull(block);
        assertEquals(MarkdownParser.Block.Kind.ORDERED_ITEM, block.getKind());
        assertEquals("第一步", plainText("1. 第一步"));
    }

    @Test
    public void dividerIsDetected() {
        MarkdownParser.Block block = firstBlock("---");
        assertNotNull(block);
        assertTrue(block.isDivider());
    }

    @Test
    public void fencedCodeBlockKeepsContentVerbatim() {
        String markdown = "```java\nint a = 1;\n**not bold**\n```";
        MarkdownParser.Block block = firstBlock(markdown);
        assertNotNull(block);
        assertTrue(block.isCodeBlock());
        assertEquals("java", block.getLanguage());
        assertEquals("int a = 1;\n**not bold**", block.getCode());
    }

    @Test
    public void unclosedFenceStillProducesCodeBlock() {
        MarkdownParser.Block block = firstBlock("```\nline one\nline two");
        assertNotNull(block);
        assertTrue(block.isCodeBlock());
        assertEquals("line one\nline two", block.getCode());
    }

    @Test
    public void blankLineSeparatesParagraphs() {
        List<MarkdownParser.Block> blocks = MarkdownParser.parse("第一段\n\n第二段");
        assertEquals(2, blocks.size());
        assertEquals(MarkdownParser.Block.Kind.PARAGRAPH, blocks.get(0).getKind());
        assertEquals("第一段 第二段", plainText("第一段\n\n第二段"));
    }

    @Test
    public void consecutiveLinesJoinIntoOneParagraph() {
        List<MarkdownParser.Block> blocks = MarkdownParser.parse("第一行\n第二行");
        assertEquals(1, blocks.size());
        assertEquals("第一行 第二行", plainText("第一行\n第二行"));
    }

    @Test
    public void emptyInputProducesNoBlocks() {
        assertTrue(MarkdownParser.parse("").isEmpty());
        assertTrue(MarkdownParser.parse(null).isEmpty());
    }

    @Test
    public void mixedRoleplayMessageParsesEndToEnd() {
        String markdown = "*推开门，雨声涌了进来*\n\n她把伞靠在墙边。\n\n> “你来了。”";
        String text = plainText(markdown);
        assertEquals("推开门，雨声涌了进来 她把伞靠在墙边。 “你来了。”", text);
        assertTrue(inlineWithText(markdown, "推开门，雨声涌了进来").isItalic());
    }
}
