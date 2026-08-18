package com.zcz.javatavern.ui;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.zcz.javatavern.R;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.media.MessageImageLoader;
import com.zcz.javatavern.network.ConversationWindow;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public final class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
    public interface AgentActionListener {
        void onConfirm(ChatMessage message);

        void onCancel(ChatMessage message);
    }

    public interface MessageActionListener {
        void onMessageLongPressed(ChatMessage message);
    }

    private final List<ChatMessage> messages = new ArrayList<>();
    private final AgentActionListener agentActionListener;
    private final MessageActionListener messageActionListener;
    private final MessageImageLoader imageLoader = new MessageImageLoader();

    public MessageAdapter(
            AgentActionListener agentActionListener,
            MessageActionListener messageActionListener
    ) {
        this.agentActionListener = agentActionListener;
        this.messageActionListener = messageActionListener;
    }

    public void replaceAll(List<ChatMessage> newMessages) {
        List<ChatMessage> oldMessages = new ArrayList<>(messages);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldMessages.size();
            }

            @Override
            public int getNewListSize() {
                return newMessages.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                ChatMessage oldMessage = oldMessages.get(oldItemPosition);
                ChatMessage newMessage = newMessages.get(newItemPosition);
                if (oldMessage.getId() > 0 && newMessage.getId() > 0) {
                    return oldMessage.getId() == newMessage.getId();
                }
                return oldMessage.getCreatedAt() == newMessage.getCreatedAt()
                        && oldMessage.getRole() == newMessage.getRole();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ChatMessage oldMessage = oldMessages.get(oldItemPosition);
                ChatMessage newMessage = newMessages.get(newItemPosition);
                return oldMessage.getKind() == newMessage.getKind()
                        && oldMessage.getContent().equals(newMessage.getContent())
                        && oldMessage.getTitle().equals(newMessage.getTitle())
                        && oldMessage.getActionState() == newMessage.getActionState()
                        && oldMessage.getAttachmentPath().equals(newMessage.getAttachmentPath())
                        && oldMessage.getReplyToMessageId() == newMessage.getReplyToMessageId()
                        && oldMessage.getReplyPreview().equals(newMessage.getReplyPreview())
                        && oldMessage.getReaction().equals(newMessage.getReaction());
            }
        });
        messages.clear();
        messages.addAll(newMessages);
        diff.dispatchUpdatesTo(this);
    }

    public void prepend(List<ChatMessage> olderMessages) {
        if (olderMessages.isEmpty()) {
            return;
        }
        messages.addAll(0, olderMessages);
        notifyItemRangeInserted(0, olderMessages.size());
    }

    public long getFirstPersistedMessageId() {
        for (ChatMessage message : messages) {
            if (message.getId() > 0) {
                return message.getId();
            }
        }
        return -1;
    }

    public void add(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLast(ChatMessage message) {
        if (messages.isEmpty()) {
            add(message);
            return;
        }
        int lastIndex = messages.size() - 1;
        messages.set(lastIndex, message);
        notifyItemChanged(lastIndex);
    }

    public void updateActionState(String actionToken, ChatMessage.ActionState actionState) {
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            if (!message.getActionToken().equals(actionToken)) {
                continue;
            }
            messages.set(index, new ChatMessage(
                    message.getId(),
                    message.getRole(),
                    message.getKind(),
                    message.getTitle(),
                    message.getContent(),
                    message.getCreatedAt(),
                    message.getActionToken(),
                    message.getActionType(),
                    actionState,
                    message.getAttachmentPath(),
                    message.getAttachmentMimeType(),
                    message.getReplyToMessageId(),
                    message.getReplyPreview(),
                    message.getReaction()
            ));
            notifyItemChanged(index);
            return;
        }
    }

    public void assignPersistedId(
            long createdAt,
            ChatMessage.Role role,
            long persistedId
    ) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatMessage message = messages.get(index);
            if (message.getId() > 0
                    || message.getCreatedAt() != createdAt
                    || message.getRole() != role) {
                continue;
            }
            messages.set(index, copyMessage(message, persistedId, message.getContent()));
            notifyItemChanged(index);
            return;
        }
    }

    public void updateMessageContent(long messageId, String content) {
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            if (message.getId() == messageId) {
                messages.set(index, copyMessage(message, messageId, content));
                notifyItemChanged(index);
                return;
            }
        }
    }

    public void removeMessage(long messageId) {
        for (int index = 0; index < messages.size(); index++) {
            if (messages.get(index).getId() == messageId) {
                messages.remove(index);
                notifyItemRemoved(index);
                return;
            }
        }
    }

    public void updateReaction(long messageId, String reaction) {
        for (int index = 0; index < messages.size(); index++) {
            ChatMessage message = messages.get(index);
            if (message.getId() != messageId) {
                continue;
            }
            messages.set(index, new ChatMessage(
                    message.getId(),
                    message.getRole(),
                    message.getKind(),
                    message.getTitle(),
                    message.getContent(),
                    message.getCreatedAt(),
                    message.getActionToken(),
                    message.getActionType(),
                    message.getActionState(),
                    message.getAttachmentPath(),
                    message.getAttachmentMimeType(),
                    message.getReplyToMessageId(),
                    message.getReplyPreview(),
                    reaction
            ));
            notifyItemChanged(index);
            return;
        }
    }

    public List<ChatMessage> snapshotRecentTextMessages(int limit) {
        return ConversationWindow.selectRecentText(messages, limit);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean isUser = message.getRole() == ChatMessage.Role.USER;
        holder.container.setGravity(isUser ? Gravity.END : Gravity.START);
        boolean isCard = message.getKind() != ChatMessage.Kind.TEXT;
        holder.bubble.setVisibility(isCard ? View.GONE : View.VISIBLE);
        holder.card.setVisibility(isCard ? View.VISIBLE : View.GONE);
        if (isCard) {
            holder.itemView.setOnLongClickListener(null);
            holder.cardTitle.setText(message.getTitle());
            holder.cardBody.setText(message.getContent());
            holder.cardBadge.setText(badgeFor(message));
            boolean canConfirm = message.getKind() == ChatMessage.Kind.AGENT_PROPOSAL
                    && message.getActionState() == ChatMessage.ActionState.PENDING;
            holder.cardActions.setVisibility(canConfirm ? View.VISIBLE : View.GONE);
            holder.confirmButton.setOnClickListener(canConfirm
                    ? view -> agentActionListener.onConfirm(message)
                    : null);
            holder.cancelButton.setOnClickListener(canConfirm
                    ? view -> agentActionListener.onCancel(message)
                    : null);
            return;
        }
        holder.itemView.setOnLongClickListener(view -> {
            messageActionListener.onMessageLongPressed(message);
            return true;
        });
        holder.cardActions.setVisibility(View.GONE);
        bindImage(holder, message);
        holder.replyPreview.setText(message.getReplyPreview());
        holder.replyPreview.setVisibility(message.hasReply() ? View.VISIBLE : View.GONE);
        holder.content.setText(message.getContent());
        holder.content.setVisibility(message.getContent().isEmpty() ? View.GONE : View.VISIBLE);
        holder.reaction.setText(message.getReaction());
        holder.reaction.setVisibility(message.hasReaction() ? View.VISIBLE : View.GONE);
        holder.bubble.setBackgroundResource(
                isUser ? R.drawable.bg_message_user : R.drawable.bg_message_assistant
        );
        holder.content.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                isUser ? android.R.color.white : R.color.text_primary
        ));
        holder.replyPreview.setTextColor(ContextCompat.getColor(
                holder.itemView.getContext(),
                isUser ? android.R.color.white : R.color.text_secondary
        ));
    }

    private void bindImage(MessageViewHolder holder, ChatMessage message) {
        String attachmentPath = message.getAttachmentPath();
        holder.boundImagePath = attachmentPath;
        holder.image.setImageDrawable(null);
        holder.image.setVisibility(message.hasImageAttachment() ? View.VISIBLE : View.GONE);
        if (!message.hasImageAttachment()) {
            return;
        }
        imageLoader.load(attachmentPath, bitmap -> {
            if (!attachmentPath.equals(holder.boundImagePath)) {
                return;
            }
            holder.image.setImageBitmap(bitmap);
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void close() {
        imageLoader.close();
    }

    private int badgeFor(ChatMessage message) {
        return switch (message.getActionState()) {
            case PENDING -> R.string.agent_action_pending;
            case CONFIRMED -> R.string.agent_action_confirmed;
            case CANCELLED -> R.string.agent_action_cancelled;
            case SUCCEEDED -> R.string.agent_action_succeeded;
            case FAILED -> R.string.agent_action_failed;
            case NONE -> R.string.agent_card_badge;
        };
    }

    private ChatMessage copyMessage(ChatMessage message, long id, String content) {
        return new ChatMessage(
                id,
                message.getRole(),
                message.getKind(),
                message.getTitle(),
                content,
                message.getCreatedAt(),
                message.getActionToken(),
                message.getActionType(),
                message.getActionState(),
                message.getAttachmentPath(),
                message.getAttachmentMimeType(),
                message.getReplyToMessageId(),
                message.getReplyPreview(),
                message.getReaction()
        );
    }

    static final class MessageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final TextView content;
        private final TextView replyPreview;
        private final TextView reaction;
        private final View bubble;
        private final ImageView image;
        private final View card;
        private final TextView cardTitle;
        private final TextView cardBody;
        private final TextView cardBadge;
        private final View cardActions;
        private final MaterialButton confirmButton;
        private final MaterialButton cancelButton;
        private String boundImagePath = "";

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.messageContainer);
            content = itemView.findViewById(R.id.messageContent);
            replyPreview = itemView.findViewById(R.id.messageReplyPreview);
            reaction = itemView.findViewById(R.id.messageReaction);
            bubble = itemView.findViewById(R.id.messageBubble);
            image = itemView.findViewById(R.id.messageImage);
            card = itemView.findViewById(R.id.agentCard);
            cardTitle = itemView.findViewById(R.id.agentCardTitle);
            cardBody = itemView.findViewById(R.id.agentCardBody);
            cardBadge = itemView.findViewById(R.id.agentCardBadge);
            cardActions = itemView.findViewById(R.id.agentCardActions);
            confirmButton = itemView.findViewById(R.id.agentConfirmButton);
            cancelButton = itemView.findViewById(R.id.agentCancelButton);
        }
    }
}
