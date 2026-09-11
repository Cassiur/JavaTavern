package com.zcz.javatavern.data;

import android.content.Context;

import com.zcz.javatavern.memory.LongTermMemoryStore;
import com.zcz.javatavern.model.CharacterCardData;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.HomeFeedItem;

import java.util.ArrayList;
import java.util.List;

public final class ChatRepository implements AutoCloseable {
    public static final class SessionData {
        private final CharacterProfile character;
        private final List<ChatMessage> messages;
        private final boolean hasMoreHistory;

        private SessionData(
                CharacterProfile character,
                List<ChatMessage> messages,
                boolean hasMoreHistory
        ) {
            this.character = character;
            this.messages = messages;
            this.hasMoreHistory = hasMoreHistory;
        }

        public CharacterProfile getCharacter() {
            return character;
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public boolean hasMoreHistory() {
            return hasMoreHistory;
        }
    }

    private final CharacterRepository characterRepository;
    private final ChatHistoryStore historyStore;
    private final LongTermMemoryStore memoryStore;

    public ChatRepository(Context context) {
        Context applicationContext = context.getApplicationContext();
        characterRepository = new CharacterRepository(applicationContext);
        historyStore = new ChatHistoryStore(applicationContext);
        memoryStore = new LongTermMemoryStore(applicationContext);
    }

    /** Delegates character-card import to the underlying character store. */
    public CharacterProfile importCard(CharacterCardData card) {
        return characterRepository.importCard(card);
    }

    /**
     * Builds the home feed: every character paired with their most recent
     * message (or greeting when none exists yet), sorted by recent activity.
     */
    public List<HomeFeedItem> loadHomeFeed() {
        List<CharacterProfile> characters = characterRepository.getCharacters();
        java.util.Map<String, ChatMessage> latestByCharacter =
                historyStore.loadLatestMessagesByCharacter();
        List<HomeFeedItem> feed = new ArrayList<>(characters.size());
        for (CharacterProfile character : characters) {
            ChatMessage latest = latestByCharacter.get(character.getId());
            String preview = latest != null ? latest.getContent() : character.getGreeting();
            long lastActivityAt = latest != null ? latest.getCreatedAt() : 0L;
            feed.add(new HomeFeedItem(character, preview, lastActivityAt));
        }
        feed.sort(HomeFeedItem.BY_RECENT);
        return feed;
    }

    public SessionData loadSession(String requestedCharacterId, int pageSize) {
        CharacterProfile character = characterRepository.findById(requestedCharacterId);
        if (character == null) {
            character = characterRepository.getDefaultCharacter();
        }
        List<ChatMessage> messages = new ArrayList<>(historyStore.loadRecentMessages(
                character.getId(),
                pageSize
        ));
        boolean hasMoreHistory = messages.size() >= pageSize;
        if (messages.isEmpty()) {
            long createdAt = System.currentTimeMillis();
            long id = historyStore.addMessage(
                    character.getId(),
                    ChatMessage.Role.ASSISTANT,
                    character.getGreeting(),
                    createdAt
            );
            messages.add(new ChatMessage(
                    id,
                    ChatMessage.Role.ASSISTANT,
                    character.getGreeting(),
                    createdAt
            ));
        }
        return new SessionData(character, messages, hasMoreHistory);
    }

    public List<ChatMessage> loadMessagesBefore(String characterId, long beforeId, int limit) {
        return historyStore.loadMessagesBefore(characterId, beforeId, limit);
    }

    /** 群聊消息：groupId 作为 character_id。 */
    public List<ChatMessage> loadGroupMessages(String groupId) {
        return historyStore.loadMessages(groupId);
    }

    /** 群聊消息写入：额外记录发言角色。 */
    public long addGroupMessage(
            String groupId,
            ChatMessage.Role role,
            String content,
            long createdAt,
            String speakerId,
            String speakerName
    ) {
        return historyStore.addGroupMessage(
                groupId, role, content, createdAt, speakerId, speakerName);
    }

    public List<ChatMessage> searchMessages(String characterId, String query, int limit) {
        return historyStore.searchMessages(characterId, query, limit);
    }

    public List<ChatMessage> loadMessageContext(
            String characterId,
            long targetId,
            int radius
    ) {
        return historyStore.loadMessageContext(characterId, targetId, radius);
    }

    public long addMessage(String characterId, ChatMessage.Role role, String content, long createdAt) {
        return historyStore.addMessage(characterId, role, content, createdAt);
    }

    public long addMessage(
            String characterId,
            ChatMessage.Role role,
            ChatMessage.Kind kind,
            String title,
            String content,
            long createdAt,
            String actionToken,
            String actionType,
            ChatMessage.ActionState actionState
    ) {
        return historyStore.addMessage(
                characterId,
                role,
                kind,
                title,
                content,
                createdAt,
                actionToken,
                actionType,
                actionState
        );
    }

    public long addMessage(
            String characterId,
            ChatMessage.Role role,
            ChatMessage.Kind kind,
            String title,
            String content,
            long createdAt,
            String actionToken,
            String actionType,
            ChatMessage.ActionState actionState,
            String attachmentPath,
            String attachmentMimeType,
            long replyToMessageId,
            String replyPreview,
            String reaction
    ) {
        return historyStore.addMessage(
                characterId,
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
                reaction
        );
    }

    public List<String> loadAttachmentPaths(String characterId) {
        return historyStore.loadAttachmentPaths(characterId);
    }

    public void updateMessageContent(long messageId, String content) {
        historyStore.updateMessageContent(messageId, content);
    }

    /** 读取单条消息（含版本信息）；不存在返回 null。 */
    public ChatMessage loadMessage(long messageId) {
        return historyStore.loadMessage(messageId);
    }

    /**
     * 把一次重 roll 的结果追加为该消息的新版本，并让它成为当前显示版本。
     * 旧回复会被保留，可用左右箭头翻回。
     */
    public void appendMessageVersion(long messageId, String content, long createdAt) {
        historyStore.appendMessageVersion(messageId, content, createdAt);
    }

    /** 切换到指定版本（1-based），返回切换后的消息。 */
    public ChatMessage switchMessageVersion(long messageId, int targetVersion) {
        return historyStore.switchMessageVersion(messageId, targetVersion);
    }

    public void deleteMessage(long messageId) {
        historyStore.deleteMessage(messageId);
    }

    public void updateMessageReaction(long messageId, String reaction) {
        historyStore.updateMessageReaction(messageId, reaction);
    }

    public ChatMessage loadPreviousUserMessage(String characterId, long beforeMessageId) {
        return historyStore.loadPreviousUserMessage(characterId, beforeMessageId);
    }

    public void updateActionState(
            String actionToken,
            ChatMessage.ActionState actionState
    ) {
        historyStore.updateActionState(actionToken, actionState);
    }

    public void addAgentAudit(
            String characterId,
            String actionToken,
            String actionType,
            String state,
            String detail,
            long createdAt
    ) {
        historyStore.addAgentAudit(
                characterId,
                actionToken,
                actionType,
                state,
                detail,
                createdAt
        );
    }

    public long clearConversationAndAddAgentResult(
            String characterId,
            String actionToken,
            String actionType,
            String title,
            String content,
            long createdAt
    ) {
        return historyStore.clearConversationAndAddAgentResult(
                characterId,
                actionToken,
                actionType,
                title,
                content,
                createdAt
        );
    }

    public String buildConfirmedMemoryPrompt(String characterId) {
        return memoryStore.buildPrompt(characterId);
    }

    @Override
    public void close() {
        historyStore.close();
        characterRepository.close();
    }
}
