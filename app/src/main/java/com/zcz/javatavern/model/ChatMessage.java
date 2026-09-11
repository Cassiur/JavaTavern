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
    private final String speakerName;
    private final int activeVersion;
    private final int versionCount;

    public ChatMessage(long id, Role role, String content, long createdAt) {
        this(id, role, Kind.TEXT, "", content, createdAt);
    }

    /** 群聊便捷构造：带发言者名。 */
    public ChatMessage(long id, Role role, String content, long createdAt, String speakerName) {
        this(
                id, role, Kind.TEXT, "", content, createdAt,
                "", "", ActionState.NONE, "", "", -1, "", "", speakerName
        );
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
                replyToMessageId,
                replyPreview,
                reaction,
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
            String reaction,
            String speakerName
    ) {
        this(
                id, role, kind, title, content, createdAt,
                actionToken, actionType, actionState,
                attachmentPath, attachmentMimeType,
                replyToMessageId, replyPreview, reaction, speakerName,
                1, 1
        );
    }

    /**
     * @param activeVersion 当前显示的版本序号（1-based）
     * @param versionCount  该消息的历史版本总数（至少 1）
     */
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
            String reaction,
            String speakerName,
            int activeVersion,
            int versionCount
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
        this.speakerName = speakerName == null ? "" : speakerName;
        this.versionCount = Math.max(1, versionCount);
        this.activeVersion = Math.min(Math.max(1, activeVersion), this.versionCount);
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

    /** 群聊中标记这条 assistant 消息是哪个角色说的（单聊为空）。 */
    public String getSpeakerName() {
        return speakerName;
    }

    /** 当前显示的版本序号（1-based）；没有多版本时为 1。 */
    public int getActiveVersion() {
        return activeVersion;
    }

    /** 该消息累计生成过的版本总数；没有多版本时为 1。 */
    public int getVersionCount() {
        return versionCount;
    }

    /** 是否有多版本可翻（重 roll 过至少一次）。 */
    public boolean hasVersions() {
        return versionCount > 1;
    }

    public boolean hasPreviousVersion() {
        return activeVersion > 1;
    }

    public boolean hasNextVersion() {
        return activeVersion < versionCount;
    }

    /** 返回一条仅版本信息不同（内容与其余字段保持不变）的消息副本。 */
    public ChatMessage withVersionInfo(int activeVersion, int versionCount) {
        return new ChatMessage(
                id, role, kind, title, content, createdAt,
                actionToken, actionType, actionState,
                attachmentPath, attachmentMimeType,
                replyToMessageId, replyPreview, reaction, speakerName,
                activeVersion, versionCount
        );
    }

    /** 返回一条内容不同（版本信息与其余字段保持不变）的消息副本。 */
    public ChatMessage withContent(String content) {
        return new ChatMessage(
                id, role, kind, title, content, createdAt,
                actionToken, actionType, actionState,
                attachmentPath, attachmentMimeType,
                replyToMessageId, replyPreview, reaction, speakerName,
                activeVersion, versionCount
        );
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
