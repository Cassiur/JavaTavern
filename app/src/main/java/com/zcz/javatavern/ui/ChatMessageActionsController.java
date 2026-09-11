package com.zcz.javatavern.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zcz.javatavern.R;
import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Extracted from {@code ChatActivity}: the per-message action menu and each
 * action's implementation (copy / reply / regenerate / react / edit / delete).
 *
 * <p>The two actions that reach back into the chat's live state — replying and
 * regenerating — are routed through {@link Listener} callbacks so this class
 * never touches the Activity's fields directly.
 */
public final class ChatMessageActionsController {

    public interface Listener {
        boolean isHostActive();

        boolean isStreaming();

        void onBeginReply(ChatMessage message);

        /**
         * 重 roll 一条已存在的消息。实现方负责把该消息从列表里换成流式气泡，
         * 并在请求成功后把结果保存为它的新版本（旧版本保留）。
         */
        void onRegenerate(ModelSettings settings, ChatMessage message);

        /** 未配置模型时的离线重 roll：直接把模拟回复存成该消息的新版本。 */
        void onRegenerateMockReply(String input, ChatMessage message);

        void onMockReply(String input);

        void onClearPendingReplyIfMatches(long messageId);

        void onDeleteImageAttachment(String attachmentPath);
    }

    private static final String[] REACTIONS = {"👍", "❤️", "😂", "😮", "😢"};

    private final Context context;
    private final ChatRepository repository;
    private final MessageAdapter adapter;
    private final SecureModelSettingsStore settingsStore;
    private final Listener listener;
    private final Supplier<String> characterId;
    private final Supplier<String> characterName;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ChatMessageActionsController(
            Context context,
            ChatRepository repository,
            MessageAdapter adapter,
            SecureModelSettingsStore settingsStore,
            Listener listener,
            Supplier<String> characterId,
            Supplier<String> characterName
    ) {
        this.context = context;
        this.repository = repository;
        this.adapter = adapter;
        this.settingsStore = settingsStore;
        this.listener = listener;
        this.characterId = characterId;
        this.characterName = characterName;
    }

    public void showActions(ChatMessage message) {
        if (message.getKind() != ChatMessage.Kind.TEXT) {
            return;
        }
        if (message.getId() <= 0) {
            copyMessage(message);
            return;
        }
        List<String> actions = new ArrayList<>();
        actions.add(context.getString(R.string.copy_message));
        actions.add(context.getString(R.string.reply_message));
        if (message.getRole() == ChatMessage.Role.ASSISTANT) {
            actions.add(context.getString(R.string.regenerate_message));
        }
        actions.add(context.getString(R.string.react_message));
        actions.add(context.getString(R.string.edit_message));
        actions.add(context.getString(R.string.delete_message));
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.message_actions)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if (action.equals(context.getString(R.string.copy_message))) {
                        copyMessage(message);
                    } else if (action.equals(context.getString(R.string.reply_message))) {
                        listener.onBeginReply(message);
                    } else if (action.equals(context.getString(R.string.regenerate_message))) {
                        regenerateMessage(message);
                    } else if (action.equals(context.getString(R.string.react_message))) {
                        showReactionPicker(message);
                    } else if (action.equals(context.getString(R.string.edit_message))) {
                        editMessage(message);
                    } else {
                        confirmDeleteMessage(message);
                    }
                })
                .show();
    }

    private void showReactionPicker(ChatMessage message) {
        String[] reactions = new String[REACTIONS.length + 1];
        System.arraycopy(REACTIONS, 0, reactions, 0, REACTIONS.length);
        reactions[reactions.length - 1] = context.getString(R.string.remove_reaction);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.react_message)
                .setItems(reactions, (dialog, which) -> {
                    String reaction = which == reactions.length - 1 ? "" : reactions[which];
                    AppExecutors.get().diskIo().execute(() -> {
                        try {
                            repository.updateMessageReaction(message.getId(), reaction);
                            mainHandler.post(() -> adapter.updateReaction(
                                    message.getId(),
                                    reaction
                            ));
                        } catch (RuntimeException exception) {
                            mainHandler.post(this::showMessageActionFailure);
                        }
                    });
                })
                .show();
    }

    private void regenerateMessage(ChatMessage message) {
        if (listener.isStreaming() || message.getRole() != ChatMessage.Role.ASSISTANT) {
            return;
        }
        String id = characterId.get();
        AppExecutors.get().diskIo().execute(() -> {
            ChatMessage source = repository.loadPreviousUserMessage(id, message.getId());
            if (source == null) {
                mainHandler.post(() -> Toast.makeText(
                        context,
                        R.string.cannot_regenerate,
                        Toast.LENGTH_SHORT
                ).show());
                return;
            }
            // 注意：这里不再删除原消息。重新生成的结果会作为它的新版本追加，
            // 用户可以在气泡上左右翻回之前的任意一版。
            mainHandler.post(() -> {
                ModelSettings settings = settingsStore.load();
                if (settings.isRemoteConfigured()) {
                    listener.onRegenerate(settings, message);
                } else {
                    String input = source.getContent().isEmpty()
                            ? context.getString(R.string.mock_image_input)
                            : source.getContent();
                    listener.onRegenerateMockReply(input, message);
                }
            });
        });
    }

    private void copyMessage(ChatMessage message) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.getContent()));
        Toast.makeText(context, R.string.message_copied, Toast.LENGTH_SHORT).show();
    }

    private void editMessage(ChatMessage message) {
        EditText editInput = new EditText(context);
        editInput.setMinLines(3);
        editInput.setMaxLines(10);
        editInput.setText(message.getContent());
        editInput.setSelection(message.getContent().length());
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_message_title)
                .setView(editInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save_changes, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String updatedContent = editInput.getText().toString().trim();
                    if (updatedContent.isEmpty() && !message.hasImageAttachment()) {
                        Toast.makeText(context, R.string.message_cannot_be_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    AppExecutors.get().diskIo().execute(() -> {
                        try {
                            repository.updateMessageContent(message.getId(), updatedContent);
                            mainHandler.post(() -> {
                                adapter.updateMessageContent(message.getId(), updatedContent);
                                Toast.makeText(context, R.string.message_updated, Toast.LENGTH_SHORT).show();
                            });
                        } catch (RuntimeException exception) {
                            mainHandler.post(this::showMessageActionFailure);
                        }
                    });
                }));
        dialog.show();
    }

    private void confirmDeleteMessage(ChatMessage message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_message_title)
                .setMessage(R.string.delete_message_description)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_message, (dialog, which) ->
                        AppExecutors.get().diskIo().execute(() -> {
                            try {
                                repository.deleteMessage(message.getId());
                                if (message.hasImageAttachment()) {
                                    listener.onDeleteImageAttachment(message.getAttachmentPath());
                                }
                                mainHandler.post(() -> {
                                    listener.onClearPendingReplyIfMatches(message.getId());
                                    adapter.removeMessage(message.getId());
                                    Toast.makeText(context, R.string.message_deleted, Toast.LENGTH_SHORT).show();
                                });
                            } catch (RuntimeException exception) {
                                mainHandler.post(this::showMessageActionFailure);
                            }
                        })
                )
                .show();
    }

    private void showMessageActionFailure() {
        Toast.makeText(context, R.string.message_action_failed, Toast.LENGTH_SHORT).show();
    }

    /** Builds the "speaker：content" reference shown in reply previews and search results. */
    public String buildReference(ChatMessage message) {
        String content = message.getContent().replace('\n', ' ').trim();
        if (content.isEmpty() && message.hasImageAttachment()) {
            content = context.getString(R.string.image_placeholder);
        }
        if (content.length() > 80) {
            content = content.substring(0, 80) + "…";
        }
        String speaker = message.getRole() == ChatMessage.Role.USER
                ? context.getString(R.string.speaker_user)
                : characterName.get();
        return speaker + "：" + content;
    }
}
