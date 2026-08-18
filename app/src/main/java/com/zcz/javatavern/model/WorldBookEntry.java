package com.zcz.javatavern.model;

import java.util.List;

public final class WorldBookEntry {
    private final List<String> keywords;
    private final String content;
    private final boolean enabled;
    private final boolean constant;

    public WorldBookEntry(
            List<String> keywords,
            String content,
            boolean enabled,
            boolean constant
    ) {
        this.keywords = List.copyOf(keywords);
        this.content = content;
        this.enabled = enabled;
        this.constant = constant;
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
}
