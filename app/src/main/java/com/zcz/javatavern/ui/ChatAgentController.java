package com.zcz.javatavern.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.zcz.javatavern.R;
import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.media.ImageAttachmentStore;
import com.zcz.javatavern.model.AgentCard;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.util.AppExecutors;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Extracted from {@code ChatActivity}: local-agent card lifecycle.
 *
 * <p>Owns rendering a routed agent card, persisting it, and the
 * confirm / cancel / fail state transitions (which are the only real agent
 * "tool calls" the app currently supports — clearing the conversation).
 */
public final class ChatAgentController {

    public interface Listener {
        boolean isHostActive();

        void scrollToLatest();
    }

    private final Context context;
    private final ChatRepository repository;
    private final MessageAdapter adapter;
    private final ImageAttachmentStore imageAttachmentStore;
    private final Listener listener;
    private final Supplier<String> characterId;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ChatAgentController(
            Context context,
            ChatRepository repository,
            MessageAdapter adapter,
            ImageAttachmentStore imageAttachmentStore,
            Listener listener,
            Supplier<String> characterId
    ) {
        this.context = context;
        this.repository = repository;
        this.adapter = adapter;
        this.imageAttachmentStore = imageAttachmentStore;
        this.listener = listener;
        this.characterId = characterId;
    }

    public void addCard(AgentCard card) {
        long createdAt = System.currentTimeMillis();
        boolean requiresConfirmation = card.requiresConfirmation();
        String actionToken = requiresConfirmation ? UUID.randomUUID().toString() : "";
        ChatMessage message = new ChatMessage(
                -1,
                ChatMessage.Role.ASSISTANT,
                requiresConfirmation
                        ? ChatMessage.Kind.AGENT_PROPOSAL
                        : ChatMessage.Kind.AGENT_CARD,
                card.getTitle(),
                card.getBody(),
                createdAt,
                actionToken,
                card.getActionType(),
                requiresConfirmation
                        ? ChatMessage.ActionState.PENDING
                        : ChatMessage.ActionState.NONE
        );
        adapter.add(message);
        listener.scrollToLatest();
        String id = characterId.get();
        AppExecutors.get().diskIo().execute(() -> {
            long rowId = repository.addMessage(
                    id,
                    message.getRole(),
                    message.getKind(),
                    message.getTitle(),
                    message.getContent(),
                    message.getCreatedAt(),
                    message.getActionToken(),
                    message.getActionType(),
                    message.getActionState()
            );
            mainHandler.post(() -> adapter.assignPersistedId(
                    createdAt,
                    ChatMessage.Role.ASSISTANT,
                    rowId
            ));
        });
        if (requiresConfirmation) {
            AppExecutors.get().diskIo().execute(() -> repository.addAgentAudit(
                    id,
                    message.getActionToken(),
                    message.getActionType(),
                    ChatMessage.ActionState.PENDING.name(),
                    message.getContent(),
                    message.getCreatedAt()
            ));
        }
    }

    public void confirm(ChatMessage proposal) {
        adapter.updateActionState(
                proposal.getActionToken(),
                ChatMessage.ActionState.CONFIRMED
        );
        if (!"clear_conversation".equals(proposal.getActionType())) {
            fail(proposal, context.getString(R.string.agent_unsupported_action));
            return;
        }
        String id = characterId.get();
        AppExecutors.get().diskIo().execute(() -> {
            long createdAt = System.currentTimeMillis();
            String title = context.getString(R.string.agent_clear_conversation_title);
            String content = context.getString(R.string.agent_clear_conversation_content);
            try {
                List<String> attachmentPaths = repository.loadAttachmentPaths(id);
                long rowId = repository.clearConversationAndAddAgentResult(
                        id,
                        proposal.getActionToken(),
                        proposal.getActionType(),
                        title,
                        content,
                        createdAt
                );
                for (String attachmentPath : attachmentPaths) {
                    imageAttachmentStore.delete(attachmentPath);
                }
                ChatMessage result = new ChatMessage(
                        rowId,
                        ChatMessage.Role.ASSISTANT,
                        ChatMessage.Kind.AGENT_RESULT,
                        title,
                        content,
                        createdAt,
                        proposal.getActionToken(),
                        proposal.getActionType(),
                        ChatMessage.ActionState.SUCCEEDED
                );
                mainHandler.post(() -> {
                    if (!listener.isHostActive()) {
                        return;
                    }
                    adapter.replaceAll(List.of(result));
                    listener.scrollToLatest();
                });
            } catch (RuntimeException exception) {
                mainHandler.post(() -> fail(proposal,
                        context.getString(R.string.agent_execute_failed)));
            }
        });
    }

    public void cancel(ChatMessage proposal) {
        adapter.updateActionState(
                proposal.getActionToken(),
                ChatMessage.ActionState.CANCELLED
        );
        String id = characterId.get();
        AppExecutors.get().diskIo().execute(() -> {
            repository.updateActionState(
                    proposal.getActionToken(),
                    ChatMessage.ActionState.CANCELLED
            );
            repository.addAgentAudit(
                    id,
                    proposal.getActionToken(),
                    proposal.getActionType(),
                    ChatMessage.ActionState.CANCELLED.name(),
                    context.getString(R.string.agent_user_cancelled),
                    System.currentTimeMillis()
            );
        });
    }

    public void fail(ChatMessage proposal, String detail) {
        adapter.updateActionState(
                proposal.getActionToken(),
                ChatMessage.ActionState.FAILED
        );
        String id = characterId.get();
        AppExecutors.get().diskIo().execute(() -> {
            repository.updateActionState(
                    proposal.getActionToken(),
                    ChatMessage.ActionState.FAILED
            );
            repository.addAgentAudit(
                    id,
                    proposal.getActionToken(),
                    proposal.getActionType(),
                    ChatMessage.ActionState.FAILED.name(),
                    detail,
                    System.currentTimeMillis()
            );
        });
    }
}
