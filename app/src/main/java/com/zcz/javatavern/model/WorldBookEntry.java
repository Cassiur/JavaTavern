package com.zcz.javatavern.model;

import java.util.List;

/**
 * 世界书条目（SillyTavern V2 character_book 兼容）。
 *
 * <p>高级字段语义（对齐 SillyTavern + AiChat）：
 * <ul>
 *   <li>{@code position}：注入位置。{@link #POSITION_BEFORE_CHAR}(0)=角色卡定义前，
 *       {@link #POSITION_AFTER_CHAR}(1)=角色卡定义后。其余取值（@D 深度注入等）
 *       仅存储保留，组装时按 after_char 处理。</li>
 *   <li>{@code order}：即 ST 的 {@code insertion_order}，同位置内升序，默认 100。</li>
 *   <li>{@code priority}：作者推荐优先级，0–99，同 order 时大者靠前，默认 0。</li>
 *   <li>{@code depth}：注入深度（position=@D 时用），默认 4，仅存储保留，不参与组装。</li>
 *   <li>{@code scanDepth}：向前扫描消息数量，默认 100。</li>
 *   <li>{@code probability}：触发概率百分比，100=总是触发，0=永不触发，默认 100。</li>
 *   <li>{@code secondaryKeys}：次要关键词列表，命中任一即激活。</li>
 *   <li>{@code caseSensitive}：关键词匹配是否区分大小写。</li>
 *   <li>{@code matchWholeWords}：是否仅整词匹配。</li>
 *   <li>{@code useGroupScoring}：使用互斥组评分算法。</li>
 *   <li>{@code automationId}：互斥组 ID，同组仅最高分条目激活。</li>
 *   <li>{@code role}：消息角色（system/user/assistant）。</li>
 *   <li>{@code vectorized}：是否已向量化（预留给语义搜索）。</li>
 *   <li>{@code sticky}：粘性，连续激活轮数（0=不粘性）。</li>
 *   <li>{@code cooldown}：冷却，休息轮数（0=无冷却）。</li>
 *   <li>{@code excludeRecursion}：递归扫描时不因其它条目命中而激活本条目。</li>
 *   <li>{@code preventRecursion}：本条目 content 不作为递归扫描的匹配文本。</li>
 * </ul>
 */
public final class WorldBookEntry {
    public static final int POSITION_BEFORE_CHAR = 0;
    public static final int POSITION_AFTER_CHAR = 1;
    public static final int DEFAULT_ORDER = 100;
    public static final int DEFAULT_DEPTH = 4;
    public static final int DEFAULT_SCAN_DEPTH = 100;
    public static final int DEFAULT_PROBABILITY = 100;
    public static final String DEFAULT_ROLE = "system";

    private final long id;
    private final List<String> keywords;
    private final List<String> secondaryKeys;
    private final String content;
    private final boolean enabled;
    private final boolean constant;
    private final int position;
    private final int order;
    private final int priority;
    private final int depth;
    private final int scanDepth;
    private final boolean caseSensitive;
    private final boolean matchWholeWords;
    private final boolean useGroupScoring;
    private final String automationId;
    private final String role;
    private final boolean vectorized;
    private final int sticky;
    private final int cooldown;
    private final int probability;
    private final boolean excludeRecursion;
    private final boolean preventRecursion;

    /** 兼容旧调用：高级字段取默认值。 */
    public WorldBookEntry(
            List<String> keywords,
            String content,
            boolean enabled,
            boolean constant
    ) {
        this(
                keywords,
                content,
                enabled,
                constant,
                POSITION_BEFORE_CHAR,
                DEFAULT_ORDER,
                0,
                DEFAULT_DEPTH,
                DEFAULT_PROBABILITY,
                false,
                false
        );
    }

    public WorldBookEntry(
            List<String> keywords,
            String content,
            boolean enabled,
            boolean constant,
            int position,
            int order,
            int priority,
            int depth,
            int probability,
            boolean excludeRecursion,
            boolean preventRecursion
    ) {
        this(
                0L,
                keywords,
                List.of(),
                content,
                enabled,
                constant,
                position,
                order,
                priority,
                depth,
                DEFAULT_SCAN_DEPTH,
                false,
                false,
                false,
                "",
                DEFAULT_ROLE,
                false,
                0,
                0,
                probability,
                excludeRecursion,
                preventRecursion
        );
    }

    /** 兼容 UI 层：包含 id 但没有新字段 */
    public WorldBookEntry(
            long id,
            List<String> keywords,
            String content,
            boolean enabled,
            boolean constant,
            int position,
            int order,
            int priority,
            int depth,
            int probability,
            boolean excludeRecursion,
            boolean preventRecursion
    ) {
        this(
                id,
                keywords,
                List.of(),
                content,
                enabled,
                constant,
                position,
                order,
                priority,
                depth,
                DEFAULT_SCAN_DEPTH,
                false,
                false,
                false,
                "",
                DEFAULT_ROLE,
                false,
                0,
                0,
                probability,
                excludeRecursion,
                preventRecursion
        );
    }

    public WorldBookEntry(
            long id,
            List<String> keywords,
            List<String> secondaryKeys,
            String content,
            boolean enabled,
            boolean constant,
            int position,
            int order,
            int priority,
            int depth,
            int scanDepth,
            boolean caseSensitive,
            boolean matchWholeWords,
            boolean useGroupScoring,
            String automationId,
            String role,
            boolean vectorized,
            int sticky,
            int cooldown,
            int probability,
            boolean excludeRecursion,
            boolean preventRecursion
    ) {
        this.id = id;
        this.keywords = List.copyOf(keywords);
        this.secondaryKeys = List.copyOf(secondaryKeys);
        this.content = content;
        this.enabled = enabled;
        this.constant = constant;
        this.position = position;
        this.order = order;
        this.priority = priority;
        this.depth = depth;
        this.scanDepth = scanDepth;
        this.caseSensitive = caseSensitive;
        this.matchWholeWords = matchWholeWords;
        this.useGroupScoring = useGroupScoring;
        this.automationId = automationId;
        this.role = role;
        this.vectorized = vectorized;
        this.sticky = sticky;
        this.cooldown = cooldown;
        this.probability = probability;
        this.excludeRecursion = excludeRecursion;
        this.preventRecursion = preventRecursion;
    }

    /** 数据库自增主键；未持久化（如刚导入的卡）时为 0。 */
    public long getId() {
        return id;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public List<String> getSecondaryKeys() {
        return secondaryKeys;
    }

    public String getContent() {
        return content;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isConstant() {
        return constant;
    }

    public int getPosition() {
        return position;
    }

    public int getOrder() {
        return order;
    }

    public int getPriority() {
        return priority;
    }

    public int getDepth() {
        return depth;
    }

    public int getScanDepth() {
        return scanDepth;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isMatchWholeWords() {
        return matchWholeWords;
    }

    public boolean isUseGroupScoring() {
        return useGroupScoring;
    }

    public String getAutomationId() {
        return automationId;
    }

    public String getRole() {
        return role;
    }

    public boolean isVectorized() {
        return vectorized;
    }

    public int getSticky() {
        return sticky;
    }

    public int getCooldown() {
        return cooldown;
    }

    public int getProbability() {
        return probability;
    }

    public boolean isExcludeRecursion() {
        return excludeRecursion;
    }

    public boolean isPreventRecursion() {
        return preventRecursion;
    }
}
