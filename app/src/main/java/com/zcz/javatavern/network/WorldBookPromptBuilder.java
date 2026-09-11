package com.zcz.javatavern.network;

import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.WorldBookEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
 *
 * <p>{@link Result#getActivations()} 会给出本轮每个被激活条目「因为哪个关键词、
 * 属于直接匹配还是第几轮递归、最终是否真的进了 prompt」——用于排查「为什么这段
 * 设定没生效」这类问题。
 */
public final class WorldBookPromptBuilder {
    private static final int MAX_WORLD_BOOK_CHARS = 4_000;
    private static final int MAX_RECURSION_STEPS = 5;
    private static final int PREVIEW_CHARS = 60;

    /** 单个条目的本轮激活情况。 */
    public static final class Activation {
        private final String contentPreview;
        private final List<String> keywords;
        private final String matchedKeyword;
        private final boolean constant;
        private final boolean recursive;
        private final int recursionStep;
        private final boolean included;
        private final boolean beforeChar;

        private Activation(
                String contentPreview,
                List<String> keywords,
                String matchedKeyword,
                boolean constant,
                boolean recursive,
                int recursionStep,
                boolean included,
                boolean beforeChar
        ) {
            this.contentPreview = contentPreview;
            this.keywords = List.copyOf(keywords);
            this.matchedKeyword = matchedKeyword;
            this.constant = constant;
            this.recursive = recursive;
            this.recursionStep = recursionStep;
            this.included = included;
            this.beforeChar = beforeChar;
        }

        /** 条目前若干个字符，便于在 UI 里识别是哪一条。 */
        public String getContentPreview() {
            return contentPreview;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        /** 实际命中的关键词；常驻条目为空字符串。 */
        public String getMatchedKeyword() {
            return matchedKeyword;
        }

        /** 是否常驻条目（无需关键词即激活）。 */
        public boolean isConstant() {
            return constant;
        }

        /** 是否由其它条目的正文递归触发。 */
        public boolean isRecursive() {
            return recursive;
        }

        /** 递归轮次；0 表示直接匹配。 */
        public int getRecursionStep() {
            return recursionStep;
        }

        /** 是否最终真的写进了 prompt（可能因字符上限被裁掉）。 */
        public boolean isIncluded() {
            return included;
        }

        /** 注入位置：true = 角色卡定义前，false = 角色卡定义后。 */
        public boolean isBeforeChar() {
            return beforeChar;
        }
    }

    /** 组装结果：beforeChar 注入角色卡定义前，afterChar 注入角色卡定义后。 */
    public static final class Result {
        private final String beforeChar;
        private final String afterChar;
        private final List<Activation> activations;

        private Result(String beforeChar, String afterChar, List<Activation> activations) {
            this.beforeChar = beforeChar;
            this.afterChar = afterChar;
            this.activations = List.copyOf(activations);
        }

        public String getBeforeChar() {
            return beforeChar;
        }

        public String getAfterChar() {
            return afterChar;
        }

        /** 本轮被激活的条目详情（按注入顺序）。 */
        public List<Activation> getActivations() {
            return activations;
        }

        public boolean isEmpty() {
            return beforeChar.isEmpty() && afterChar.isEmpty();
        }
    }

    public Result build(List<WorldBookEntry> entries, List<ChatMessage> conversation) {
        String searchableText = recentConversationText(conversation);
        int size = entries.size();
        boolean[] active = new boolean[size];
        String[] matchedKeywords = new String[size];
        boolean[] recursive = new boolean[size];
        int[] recursionSteps = new int[size];

        for (int index = 0; index < size; index++) {
            WorldBookEntry entry = entries.get(index);
            if (!entry.isEnabled()) {
                continue;
            }
            String matched = entry.isConstant() ? "" : findMatchingKeyword(entry, searchableText);
            if (matched == null || !passesProbability(entry)) {
                continue;
            }
            active[index] = true;
            matchedKeywords[index] = matched;
        }

        for (int step = 1; step <= MAX_RECURSION_STEPS; step++) {
            String recursionText = recursionText(entries, active);
            if (recursionText.isEmpty()) {
                break;
            }
            boolean changed = false;
            for (int index = 0; index < size; index++) {
                if (active[index]) {
                    continue;
                }
                WorldBookEntry entry = entries.get(index);
                if (!entry.isEnabled() || entry.isExcludeRecursion()) {
                    continue;
                }
                String matched = findMatchingKeyword(entry, recursionText);
                if (matched == null || !passesProbability(entry)) {
                    continue;
                }
                active[index] = true;
                matchedKeywords[index] = matched;
                recursive[index] = true;
                recursionSteps[index] = step;
                changed = true;
            }
            if (!changed) {
                break;
            }
        }

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
        Set<Integer> includedIndices = new HashSet<>();
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
            includedIndices.add(index);
        }

        List<Activation> activations = new ArrayList<>(activeIndices.size());
        for (int index : activeIndices) {
            WorldBookEntry entry = entries.get(index);
            String matched = matchedKeywords[index];
            activations.add(new Activation(
                    preview(entry.getContent()),
                    entry.getKeywords(),
                    matched == null ? "" : matched,
                    entry.isConstant(),
                    recursive[index],
                    recursionSteps[index],
                    includedIndices.contains(index),
                    isBeforeChar(entry)
            ));
        }
        return new Result(before.toString(), after.toString(), activations);
    }

    private String preview(String content) {
        String trimmed = content.trim();
        if (trimmed.length() <= PREVIEW_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, PREVIEW_CHARS) + "…";
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

    /** 返回命中的关键词；未命中返回 null。 */
    private String findMatchingKeyword(WorldBookEntry entry, String text) {
        for (String keyword : entry.getKeywords()) {
            if (matchesKeyword(keyword, text)) {
                return keyword;
            }
        }
        return null;
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
