package com.zcz.javatavern.model;

public final class AgentCard {
    private final String title;
    private final String body;
    private final String badge;
    private final String actionType;

    public AgentCard(String title, String body, String badge) {
        this(title, body, badge, "");
    }

    public AgentCard(String title, String body, String badge, String actionType) {
        this.title = title;
        this.body = body;
        this.badge = badge;
        this.actionType = actionType;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getBadge() {
        return badge;
    }

    public String getActionType() {
        return actionType;
    }

    public boolean requiresConfirmation() {
        return !actionType.isEmpty();
    }
}
