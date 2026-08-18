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
    private final long replyToMessageId;
    private final String replyPreview;
    private final String reaction;

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
                attachmentPath,
                attachmentMimeType,
                -1,
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
            String attachmentMimeType,
            long replyToMessageId,
            String replyPreview,
            String reaction
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
        this.replyToMessageId = replyToMessageId;
        this.replyPreview = replyPreview;
        this.reaction = reaction;
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

    public long getReplyToMessageId() {
        return replyToMessageId;
    }

    public String getReplyPreview() {
        return replyPreview;
    }

    public String getReaction() {
        return reaction;
    }

    public boolean hasImageAttachment() {
        return !attachmentPath.isEmpty() && attachmentMimeType.startsWith("image/");
    }

    public boolean hasReply() {
        return replyToMessageId > 0 && !replyPreview.isEmpty();
    }

    public boolean hasReaction() {
        return !reaction.isEmpty();
    }
}
