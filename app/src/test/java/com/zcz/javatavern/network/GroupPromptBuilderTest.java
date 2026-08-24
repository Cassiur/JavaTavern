package com.zcz.javatavern.network;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import com.zcz.javatavern.model.CharacterProfile;

import org.junit.Test;

import java.util.List;

public final class GroupPromptBuilderTest {

    private final GroupPromptBuilder builder = new GroupPromptBuilder();

    private static CharacterProfile character(
            String id, String name, String description, String systemPrompt) {
        return new CharacterProfile(
                id, name, description, "开场白", 0, systemPrompt, List.of());
    }

    @Test
    public void includesAllMemberSummaries() {
        CharacterProfile a = character("a", "艾琳", "北方雪原的少女剑士", "设定A");
        CharacterProfile b = character("b", "薇拉", "深夜酒馆的老板娘", "设定B");

        String prompt = builder.buildSystemPrompt(
                List.of(a, b), a, "", "", "");

        assertTrue(prompt.contains("艾琳：北方雪原的少女剑士"));
        assertTrue(prompt.contains("薇拉：深夜酒馆的老板娘"));
        assertTrue(prompt.contains("2 位角色在场"));
    }

    @Test
    public void injectsSpeakerFullCardAndRule() {
        CharacterProfile a = character("a", "艾琳", "少女剑士", "艾琳的完整设定");
        CharacterProfile b = character("b", "薇拉", "老板娘", "薇拉的完整设定");

        String prompt = builder.buildSystemPrompt(
                List.of(a, b), a, "", "", "");

        assertTrue(prompt.contains("现在轮到你扮演「艾琳」发言"));
        assertTrue(prompt.contains("你是艾琳。艾琳的完整设定"));
        assertTrue(prompt.contains("不要替其他角色说话"));
        // 非发言者的完整卡不应注入
        assertFalse(prompt.contains("薇拉的完整设定"));
    }

    @Test
    public void nonSpeakerMemberOnlyAppearsAsSummary() {
        CharacterProfile a = character("a", "艾琳", "少女剑士", "艾琳完整设定");
        CharacterProfile b = character("b", "薇拉", "老板娘", "薇拉完整设定");

        String prompt = builder.buildSystemPrompt(
                List.of(a, b), a, "", "", "");

        // 薇拉只以摘要形式出现，不含其 systemPrompt
        assertTrue(prompt.contains("薇拉：老板娘"));
        assertFalse(prompt.contains("薇拉完整设定"));
    }

    @Test
    public void placesWorldBookBeforeAndAfter() {
        CharacterProfile a = character("a", "艾琳", "少女剑士", "设定A");
        CharacterProfile b = character("b", "薇拉", "老板娘", "设定B");

        String prompt = builder.buildSystemPrompt(
                List.of(a, b), a, "【世界书前】", "【世界书后】", "");

        int beforeIdx = prompt.indexOf("【世界书前】");
        int speakerIdx = prompt.indexOf("你是艾琳");
        int afterIdx = prompt.indexOf("【世界书后】");
        assertTrue(beforeIdx >= 0);
        assertTrue(speakerIdx >= 0);
        assertTrue(afterIdx >= 0);
        assertTrue(beforeIdx < speakerIdx);
        assertTrue(speakerIdx < afterIdx);
    }

    @Test
    public void injectsConfirmedMemory() {
        CharacterProfile a = character("a", "艾琳", "少女剑士", "设定A");
        CharacterProfile b = character("b", "薇拉", "老板娘", "设定B");

        String prompt = builder.buildSystemPrompt(
                List.of(a, b), a, "", "", "用户喜欢的茶是茉莉花茶");

        assertTrue(prompt.contains("用户喜欢的茶是茉莉花茶"));
    }

    @Test
    public void truncatesLongMemberDescription() {
        String longDescription = "很长的描述".repeat(50); // 250 字符
        CharacterProfile a = character("a", "艾琳", longDescription, "设定A");
        CharacterProfile b = character("b", "薇拉", "老板娘", "设定B");

        String prompt = builder.buildSystemPrompt(
                List.of(a, b), a, "", "", "");

        // 摘要被截断到 120 字符 + 省略号
        assertTrue(prompt.contains("…"));
        assertFalse(prompt.contains(longDescription));
    }
}
