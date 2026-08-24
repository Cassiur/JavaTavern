package com.zcz.javatavern.network;

import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.WorldBookEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 世界书激活与组装（SillyTavern 兼容）。
 *
 * <p>激活判定分两阶段：
 * <ol>
 *   <li><b>直接匹配</b>：constant 条目恒激活；否则最近对话文本匹配任一关键词
 *       （普通子串 contains，或 {@code /pattern/} 形式的正则）。</li>
 *   <li><b>递归扫描</b>：已激活条目（且未 preventRecursion）的 content 作为新匹配文本，
 *       继续激活其它条目；excludeRecursion 的条目跳过；用 visited 标记防循环，
 *       最多 {@link #MAX_RECURSION_STEPS} 轮。</li>
 * </ol>
 * probability 在每次命中时抽样（100=恒触发，0=永不触发）。
 * 激活后按 (position, order 升序, priority 降序) 排序，分 before_char / after_char 两桶返回。
 */
public final class WorldBookPromptBuilder {
    private static final int MAX_WORLD_BOOK_CHARS = 4_000;
    private static final int MAX_RECURSION_STEPS = 5;

    /** 组装结果：beforeChar 注入角色卡定义前，afterChar 注入角色卡定义后。 */
    public static final class Result {
        private final String beforeChar;
        private final String afterChar;

        private Result(String beforeChar, String afterChar) {
            this.beforeChar = beforeChar;
            this.afterChar = afterChar;
        }

        public String getBeforeChar() {
            return beforeChar;
        }

        public String getAfterChar() {
            return afterChar;
        }

        public boolean isEmpty() {
            return beforeChar.isEmpty() && afterChar.isEmpty();
        }
    }

    public Result build(List<WorldBookEntry> entries, List<ChatMessage> conversation) {
        String searchableText = recentConversationText(conversation);
        int size = entries.size();
        boolean[] active = new boolean[size];

        for (int index = 0; index < size; index++) {
            WorldBookEntry entry = entries.get(index);
            if (entry.isEnabled()
                    && isDirectlyActivated(entry, searchableText)
                    && passesProbability(entry)) {
                active[index] = true;
            }
        }

        scanRecursively(entries, active);

        List<Integer> activeIndices = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            if (active[index]) {
                activeIndices.add(index);
            }
        }
        activeIndices.sort(Comparator
                .comparingInt((Integer index) -> isBeforeChar(entries.get(index)) ? 0 : 1)
                .thenComparingInt(index -> entries.get(index).getOrder())
                .thenComparingInt(index -> -entries.get(index).getPriority()));

        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        int totalChars = 0;
        for (int index : activeIndices) {
            String block = entries.get(index).getContent().trim();
            if (block.isEmpty()) {
                continue;
            }
            if (totalChars + block.length() > MAX_WORLD_BOOK_CHARS) {
                break;
            }
            StringBuilder target = isBeforeChar(entries.get(index)) ? before : after;
            if (target.length() > 0) {
                target.append("\n\n");
            }
            target.append(block);
            totalChars += block.length();
        }
        return new Result(before.toString(), after.toString());
    }

    private void scanRecursively(List<WorldBookEntry> entries, boolean[] active) {
        for (int step = 0; step < MAX_RECURSION_STEPS; step++) {
            String recursionText = recursionText(entries, active);
            if (recursionText.isEmpty()) {
                break;
            }
            boolean changed = false;
            for (int index = 0; index < entries.size(); index++) {
                if (active[index]) {
                    continue;
                }
                WorldBookEntry entry = entries.get(index);
                if (!entry.isEnabled() || entry.isExcludeRecursion()) {
                    continue;
                }
                if (matchesAnyKeyword(entry, recursionText) && passesProbability(entry)) {
                    active[index] = true;
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    private String recursionText(List<WorldBookEntry> entries, boolean[] active) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            if (active[index] && !entries.get(index).isPreventRecursion()) {
                text.append(entries.get(index).getContent()).append('\n');
            }
        }
        return text.toString();
    }

    private boolean isDirectlyActivated(WorldBookEntry entry, String searchableText) {
        return entry.isConstant() || matchesAnyKeyword(entry, searchableText);
    }

    private boolean matchesAnyKeyword(WorldBookEntry entry, String text) {
        for (String keyword : entry.getKeywords()) {
            if (matchesKeyword(keyword, text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * SillyTavern 关键词匹配：以 {@code /pattern/} 或 {@code /pattern/flags} 包裹的
     * 关键词按正则匹配（默认大小写不敏感，{@code s} 标志表敏感），否则回退为
     * 大小写不敏感的普通子串匹配。
     */
    private boolean matchesKeyword(String keyword, String text) {
        Pattern regex = parseRegexKeyword(keyword);
        if (regex != null) {
            return regex.matcher(text).find();
        }
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    /**
     * 解析 {@code /pattern/} 或 {@code /pattern/flags} 形式的关键词。
     *
     * @return 编译后的正则；若不是合法正则关键词（不以 {@code /} 开头、无闭合
     *         {@code /}、或正则语法错误）则返回 {@code null}，交由普通子串匹配处理。
     */
    private Pattern parseRegexKeyword(String keyword) {
        if (keyword.length() < 3 || !keyword.startsWith("/")) {
            return null;
        }
        int lastSlash = keyword.lastIndexOf('/');
        if (lastSlash <= 1) {
            return null;
        }
        String body = keyword.substring(1, lastSlash);
        String flags = keyword.substring(lastSlash + 1);
        int javaFlags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        if (flags.contains("s") || flags.contains("S")) {
            javaFlags = 0;
        }
        try {
            return Pattern.compile(body, javaFlags);
        } catch (PatternSyntaxException exception) {
            return null;
        }
    }

    private boolean passesProbability(WorldBookEntry entry) {
        int probability = entry.getProbability();
        if (probability >= 100) {
            return true;
        }
        if (probability <= 0) {
            return false;
        }
        return Math.random() * 100.0 < probability;
    }

    private boolean isBeforeChar(WorldBookEntry entry) {
        return entry.getPosition() == WorldBookEntry.POSITION_BEFORE_CHAR;
    }

    private String recentConversationText(List<ChatMessage> conversation) {
        StringBuilder text = new StringBuilder();
        int start = Math.max(0, conversation.size() - 12);
        for (int index = start; index < conversation.size(); index++) {
            ChatMessage message = conversation.get(index);
            if (message.getKind() == ChatMessage.Kind.TEXT) {
                text.append(message.getContent()).append('\n');
            }
        }
        return text.toString();
    }
}
