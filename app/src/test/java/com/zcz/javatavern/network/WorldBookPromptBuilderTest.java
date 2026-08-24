package com.zcz.javatavern.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.WorldBookEntry;

import org.junit.Test;

import java.util.List;

public final class WorldBookPromptBuilderTest {

    private final WorldBookPromptBuilder builder = new WorldBookPromptBuilder();

    private static ChatMessage msg(String content) {
        return new ChatMessage(1L, ChatMessage.Role.USER, content, 0L);
    }

    private static WorldBookEntry entry(String key, String content, int position) {
        return entry(key, content, position, 100, 0, 100, false, false);
    }

    private static WorldBookEntry entry(
            String key,
            String content,
            int position,
            int order,
            int priority,
            int probability,
            boolean excludeRecursion,
            boolean preventRecursion
    ) {
        return new WorldBookEntry(
                List.of(key),
                content,
                true,
                false,
                position,
                order,
                priority,
                4,
                probability,
                excludeRecursion,
                preventRecursion
        );
    }

    @Test
    public void directMatch_splitsBeforeAndAfter() {
        WorldBookEntry before = entry("猫", "猫咪设定", WorldBookEntry.POSITION_BEFORE_CHAR);
        WorldBookEntry after = entry("狗", "狗狗设定", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(before, after), List.of(msg("我有一只猫和一条狗")));

        assertEquals("猫咪设定", result.getBeforeChar());
        assertEquals("狗狗设定", result.getAfterChar());
    }

    @Test
    public void noMatch_returnsEmpty() {
        WorldBookEntry entry = entry("猫", "猫咪设定", WorldBookEntry.POSITION_BEFORE_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("今天天气不错")));

        assertTrue(result.isEmpty());
    }

    @Test
    public void order_ascending_sortsEntries() {
        WorldBookEntry later = entry("猫", "第一段", WorldBookEntry.POSITION_AFTER_CHAR, 200, 0, 100, false, false);
        WorldBookEntry earlier = entry("猫", "第二段", WorldBookEntry.POSITION_AFTER_CHAR, 50, 0, 100, false, false);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(later, earlier), List.of(msg("我养猫")));

        assertTrue(result.getAfterChar().indexOf("第二段") < result.getAfterChar().indexOf("第一段"));
    }

    @Test
    public void priority_descending_breaksOrderTies() {
        WorldBookEntry low = entry("猫", "低优先级", WorldBookEntry.POSITION_AFTER_CHAR, 100, 1, 100, false, false);
        WorldBookEntry high = entry("猫", "高优先级", WorldBookEntry.POSITION_AFTER_CHAR, 100, 90, 100, false, false);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(low, high), List.of(msg("我养猫")));

        assertTrue(result.getAfterChar().indexOf("高优先级") < result.getAfterChar().indexOf("低优先级"));
    }

    @Test
    public void probability_zero_neverActivates() {
        WorldBookEntry entry = entry("猫", "不该出现", WorldBookEntry.POSITION_AFTER_CHAR, 100, 0, 0, false, false);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("我养猫")));

        assertTrue(result.isEmpty());
    }

    @Test
    public void recursiveScan_activatesChainedEntries() {
        WorldBookEntry trigger = entry("猫", "我喜欢狗", WorldBookEntry.POSITION_AFTER_CHAR);
        WorldBookEntry chained = entry("狗", "狗狗很忠诚", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(trigger, chained), List.of(msg("我养了一只猫")));

        assertTrue(result.getAfterChar().contains("我喜欢狗"));
        assertTrue(result.getAfterChar().contains("狗狗很忠诚"));
    }

    @Test
    public void excludeRecursion_blocksRecursiveActivation() {
        WorldBookEntry trigger = entry("猫", "我喜欢狗", WorldBookEntry.POSITION_AFTER_CHAR);
        WorldBookEntry blocked = entry("狗", "不该递归出现", WorldBookEntry.POSITION_AFTER_CHAR,
                100, 0, 100, true, false);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(trigger, blocked), List.of(msg("我养了一只猫")));

        assertTrue(result.getAfterChar().contains("我喜欢狗"));
        assertFalse(result.getAfterChar().contains("不该递归出现"));
    }

    @Test
    public void preventRecursion_stopsContentAsScanText() {
        WorldBookEntry trigger = entry("猫", "我喜欢狗", WorldBookEntry.POSITION_AFTER_CHAR,
                100, 0, 100, false, true);
        WorldBookEntry chained = entry("狗", "不该被激活", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(trigger, chained), List.of(msg("我养了一只猫")));

        assertTrue(result.getAfterChar().contains("我喜欢狗"));
        assertFalse(result.getAfterChar().contains("不该被激活"));
    }

    @Test
    public void recursion_doesNotLoopForever() {
        WorldBookEntry a = entry("猫", "提到狗", WorldBookEntry.POSITION_AFTER_CHAR);
        WorldBookEntry b = entry("狗", "提到猫", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(a, b), List.of(msg("我养了一只猫")));

        // 两段都应被激活（a 直接命中，b 被 a 的 content 递归激活），且不会死循环。
        assertTrue(result.getAfterChar().contains("提到狗"));
        assertTrue(result.getAfterChar().contains("提到猫"));
    }

    @Test
    public void disabledEntry_neverActivates() {
        WorldBookEntry entry = new WorldBookEntry(
                List.of("猫"), "不该出现", false, false);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("我养猫")));

        assertTrue(result.isEmpty());
    }

    @Test
    public void regexKeyword_matchesByPattern() {
        // /^你.*吗$/ 匹配以"你"开头以"吗"结尾的疑问句
        WorldBookEntry entry = entry("/^你.*吗$/", "疑问句设定", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("你今晚有空吗")));

        assertTrue(result.getAfterChar().contains("疑问句设定"));
    }

    @Test
    public void regexKeyword_noMatchWhenPatternDiffers() {
        WorldBookEntry entry = entry("/^你.*吗$/", "疑问句设定", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("今天天气不错")));

        assertTrue(result.isEmpty());
    }

    @Test
    public void regexKeyword_defaultCaseInsensitive() {
        WorldBookEntry entry = entry("/dragon/", "龙设定", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("a DRAGON appears")));

        assertTrue(result.getAfterChar().contains("龙设定"));
    }

    @Test
    public void regexKeyword_sensitiveFlagIsCaseSensitive() {
        WorldBookEntry entry = entry("/dragon/s", "龙设定", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("a DRAGON appears")));

        assertTrue(result.isEmpty());
    }

    @Test
    public void regexKeyword_invalidRegexFallsBackToLiteral() {
        // 非法正则（未闭合括号）回退为普通子串匹配，此时不命中
        WorldBookEntry entry = entry("/[abc/", "不该出现", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(entry), List.of(msg("abc")));

        assertTrue(result.isEmpty());
    }

    @Test
    public void regexKeyword_activatesInRecursiveScan() {
        WorldBookEntry trigger = entry("猫", "我提到 DRAGON", WorldBookEntry.POSITION_AFTER_CHAR);
        WorldBookEntry chained = entry("/dragon/", "龙相关设定", WorldBookEntry.POSITION_AFTER_CHAR);

        WorldBookPromptBuilder.Result result = builder.build(
                List.of(trigger, chained), List.of(msg("我养猫")));

        assertTrue(result.getAfterChar().contains("我提到 DRAGON"));
        assertTrue(result.getAfterChar().contains("龙相关设定"));
    }
}
