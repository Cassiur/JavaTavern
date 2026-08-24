package com.zcz.javatavern.model;

import java.util.List;

/**
 * 世界书条目（SillyTavern V2 character_book 兼容）。
 *
 * <p>高级字段语义（对齐 SillyTavern + AiChat）：
 * <ul>
 *   <li>{@code position}：注入位置。{@link #POSITION_BEFORE_CHAR}(0)=角色卡定义前，
 *       {@link #POSITION_AFTER_CHAR}(1)=角色卡定义后。其余取值（@D 深度注入等）本轮
 *       仅存储保留，组装时按 after_char 处理。</li>
 *   <li>{@code order}：即 ST 的 {@code insertion_order}，同位置内升序，默认 100。</li>
 *   <li>{@code priority}：作者推荐优先级，0–99，同 order 时大者靠前，默认 0。</li>
 *   <li>{@code depth}：注入深度（position=@D 时用），默认 4，本轮仅存储保留。</li>
 *   <li>{@code probability}：触发概率百分比，100=总是触发，0=永不触发，默认 100。</li>
 *   <li>{@code excludeRecursion}：递归扫描时不因其它条目命中而激活本条目。</li>
 *   <li>{@code preventRecursion}：本条目 content 不作为递归扫描的匹配文本。</li>
 * </ul>
 */
public final class WorldBookEntry {
    public static final int POSITION_BEFORE_CHAR = 0;
    public static final int POSITION_AFTER_CHAR = 1;
    public static final int DEFAULT_ORDER = 100;
    public static final int DEFAULT_DEPTH = 4;
    public static final int DEFAULT_PROBABILITY = 100;

    private final long id;
    private final List<String> keywords;
    private final String content;
    private final boolean enabled;
    private final boolean constant;
    private final int position;
    private final int order;
    private final int priority;
    private final int depth;
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
                content,
                enabled,
                constant,
                position,
                order,
                priority,
                depth,
                probability,
                excludeRecursion,
                preventRecursion
        );
    }

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
        this.id = id;
        this.keywords = List.copyOf(keywords);
        this.content = content;
        this.enabled = enabled;
        this.constant = constant;
        this.position = position;
        this.order = order;
        this.priority = priority;
        this.depth = depth;
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
