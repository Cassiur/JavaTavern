package com.zcz.javatavern.model;

public final class ChatMessage {
    public enum Kind {
        TEXT,
        AGENT_CARD,
        AGENT_PROPOSAL,
        AGENT_RESULT
    }

    public enum ActionState {
        NONE,
        PENDING,
        CONFIRMED,
        CANCELLED,
        SUCCEEDED,
        FAILED
    }

    public enum Role {
        USER,
        ASSISTANT
    }

    private final long id;
    private final Role role;
    private final Kind kind;
    private final String title;
    private final String content;
    private final long createdAt;
    private final String actionToken;
    private final String actionType;
    private final ActionState actionState;
    private final String attachmentPath;
    private final String attachmentMimeType;

    public ChatMessage(long id, Role role, String content, long createdAt) {
        this(id, role, Kind.TEXT, "", content, createdAt);
    }

    public ChatMessage(
            long id,
            Role role,
            Kind kind,
            String title,
            String content,
            long createdAt
    ) {
        this(id, role, kind, title, content, createdAt, "", "", ActionState.NONE, "", "");
    }

    public ChatMessage(
            long id,
            Role role,
            Kind kind,
            String title,
            String content,
            long createdAt,
            String actionToken,
            String actionType,
            ActionState actionState
    ) {
        this(
                id,
                role,
                kind,
                title,
                content,
                createdAt,
                actionToken,
                actionType,
                actionState,
                "",
                ""
        );
    }

    public ChatMessage(
            long id,
            Role role,
            Kind kind,
            String title,
            String content,
            long createdAt,
            String actionToken,
            String actionType,
            ActionState actionState,
            String attachmentPath,
            String attachmentMimeType
    ) {
        this.id = id;
        this.role = role;
        this.kind = kind;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.actionToken = actionToken;
        this.actionType = actionType;
        this.actionState = actionState;
        this.attachmentPath = attachmentPath;
        this.attachmentMimeType = attachmentMimeType;
    }

    public long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public Kind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getActionToken() {
        return actionToken;
    }

    public String getActionType() {
        return actionType;
    }

    public ActionState getActionState() {
        return actionState;
    }

    public String getAttachmentPath() {
        return attachmentPath;
    }

    public String getAttachmentMimeType() {
        return attachmentMimeType;
    }

    public boolean hasImageAttachment() {
        return !attachmentPath.isEmpty() && attachmentMimeType.startsWith("image/");
    }
}
