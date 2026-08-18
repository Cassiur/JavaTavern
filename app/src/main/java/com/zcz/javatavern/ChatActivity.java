package com.zcz.javatavern;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zcz.javatavern.agent.LocalAgentRouter;
import com.zcz.javatavern.data.CharacterRepository;
import com.zcz.javatavern.data.ChatHistoryStore;
import com.zcz.javatavern.data.ConversationDraftStore;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.model.AgentCard;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.media.ImageAttachmentStore;
import com.zcz.javatavern.network.OpenAiCompatibleClient;
import com.zcz.javatavern.service.MockReplyEngine;
import com.zcz.javatavern.service.ReplyEngine;
import com.zcz.javatavern.ui.MessageAdapter;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ChatActivity extends AppCompatActivity {
    private static final int INITIAL_PAGE_SIZE = 60;
    private static final int OLDER_PAGE_SIZE = 40;
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ReplyEngine replyEngine = new MockReplyEngine();
    private final LocalAgentRouter agentRouter = new LocalAgentRouter();
    private final OpenAiCompatibleClient modelClient = new OpenAiCompatibleClient();

    private CharacterProfile character;
    private String characterId = "";
    private CharacterRepository characterRepository;
    private ChatHistoryStore historyStore;
    private ConversationDraftStore draftStore;
    private SecureModelSettingsStore settingsStore;
    private MessageAdapter messageAdapter;
    private RecyclerView messageList;
    private LinearLayoutManager messageLayoutManager;
    private EditText messageInput;
    private MaterialButton sendButton;
    private MaterialButton attachImageButton;
    private View attachmentPreview;
    private ImageView attachmentPreviewImage;
    private View replyPreview;
    private TextView replyPreviewText;
    private ChatMessage pendingReplyMessage;
    private ImageAttachmentStore imageAttachmentStore;
    private ActivityResultLauncher<PickVisualMediaRequest> imagePicker;
    private String pendingAttachmentPath = "";
    private String pendingAttachmentMimeType = "";
    private boolean loadingOlderMessages;
    private boolean hasMoreHistory = true;
    private OpenAiCompatibleClient.StreamCall activeStream;
    private StringBuilder streamingText;
    private final StringBuilder pendingStreamDeltas = new StringBuilder();
    private final Object streamBufferLock = new Object();
    private final Runnable streamRenderRunnable = this::flushStreamDeltas;
    private boolean streamRenderScheduled;
    private long streamingCreatedAt;
    private final Runnable persistDraftRunnable = this::persistDraft;
    private boolean restoringDraft;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        String requestedCharacterId = getIntent().getStringExtra(MainActivity.EXTRA_CHARACTER_ID);
        characterRepository = new CharacterRepository(getApplicationContext());
        historyStore = new ChatHistoryStore(getApplicationContext());
        draftStore = new ConversationDraftStore(getApplicationContext());
        settingsStore = new SecureModelSettingsStore(getApplicationContext());
        imageAttachmentStore = new ImageAttachmentStore(getApplicationContext());

        TextView title = findViewById(R.id.chatTitle);
        title.setText(R.string.loading_character);
        findViewById(R.id.backButton).setOnClickListener(view -> finish());

        messageAdapter = new MessageAdapter(new MessageAdapter.AgentActionListener() {
            @Override
            public void onConfirm(ChatMessage message) {
                confirmAgentAction(message);
            }

            @Override
            public void onCancel(ChatMessage message) {
                cancelAgentAction(message);
            }
        }, this::showMessageActions);
        messageList = findViewById(R.id.messageList);
        messageLayoutManager = new LinearLayoutManager(this);
        messageLayoutManager.setStackFromEnd(true);
        messageList.setLayoutManager(messageLayoutManager);
        messageList.setAdapter(messageAdapter);
        messageList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy < 0 && messageLayoutManager.findFirstVisibleItemPosition() <= 2) {
                    loadOlderMessages();
                }
            }
        });

        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        attachImageButton = findViewById(R.id.attachImageButton);
        attachmentPreview = findViewById(R.id.attachmentPreview);
        attachmentPreviewImage = findViewById(R.id.attachmentPreviewImage);
        replyPreview = findViewById(R.id.replyPreview);
        replyPreviewText = findViewById(R.id.replyPreviewText);
        messageInput.setEnabled(false);
        sendButton.setEnabled(false);
        attachImageButton.setEnabled(false);
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.PickVisualMedia(),
                this::prepareImageAttachment
        );
        attachImageButton.setOnClickListener(view -> imagePicker.launch(
                new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()
        ));
        findViewById(R.id.removeAttachmentButton).setOnClickListener(view ->
                clearPendingAttachment(true)
        );
        findViewById(R.id.removeReplyButton).setOnClickListener(view -> clearPendingReply());
        findViewById(R.id.searchMessagesButton).setOnClickListener(view -> showSearchDialog());
        sendButton.setOnClickListener(view -> handlePrimaryAction());
        messageInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                handlePrimaryAction();
                return true;
            }
            return false;
        });
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (restoringDraft || characterId.isEmpty()) {
                    return;
                }
                mainHandler.removeCallbacks(persistDraftRunnable);
                mainHandler.postDelayed(persistDraftRunnable, 300);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        loadHistory(requestedCharacterId == null ? "" : requestedCharacterId, title);
    }

    private void loadHistory(String requestedCharacterId, TextView title) {
        databaseExecutor.execute(() -> {
            CharacterProfile loadedCharacter = characterRepository.findById(requestedCharacterId);
            if (loadedCharacter == null) {
                loadedCharacter = characterRepository.getDefaultCharacter();
            }
            List<ChatMessage> messages = historyStore.loadRecentMessages(
                    loadedCharacter.getId(),
                    INITIAL_PAGE_SIZE
            );
            boolean moreHistoryAvailable = messages.size() >= INITIAL_PAGE_SIZE;
            if (messages.isEmpty()) {
                long createdAt = System.currentTimeMillis();
                long id = historyStore.addMessage(
                        loadedCharacter.getId(),
                        ChatMessage.Role.ASSISTANT,
                        loadedCharacter.getGreeting(),
                        createdAt
                );
                messages.add(new ChatMessage(
                        id,
                        ChatMessage.Role.ASSISTANT,
                        loadedCharacter.getGreeting(),
                        createdAt
                ));
            }
            CharacterProfile resultCharacter = loadedCharacter;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                character = resultCharacter;
                this.characterId = character.getId();
                hasMoreHistory = moreHistoryAvailable;
                title.setText(character.getName());
                messageAdapter.replaceAll(messages);
                String draft = draftStore.load(characterId);
                restoringDraft = true;
                messageInput.setText(draft);
                messageInput.setSelection(draft.length());
                restoringDraft = false;
                messageInput.setEnabled(true);
                sendButton.setEnabled(true);
                attachImageButton.setEnabled(true);
                scrollToLatest();
            });
        });
    }

    private void loadOlderMessages() {
        if (loadingOlderMessages || !hasMoreHistory || characterId.isEmpty()) {
            return;
        }
        long beforeId = messageAdapter.getFirstPersistedMessageId();
        if (beforeId <= 0) {
            hasMoreHistory = false;
            return;
        }
        int anchorPosition = messageLayoutManager.findFirstVisibleItemPosition();
        View anchorView = messageLayoutManager.findViewByPosition(anchorPosition);
        int anchorOffset = anchorView == null ? 0 : anchorView.getTop() - messageList.getPaddingTop();
        loadingOlderMessages = true;
        databaseExecutor.execute(() -> {
            List<ChatMessage> olderMessages = historyStore.loadMessagesBefore(
                    characterId,
                    beforeId,
                    OLDER_PAGE_SIZE
            );
            mainHandler.post(() -> {
                loadingOlderMessages = false;
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                hasMoreHistory = olderMessages.size() >= OLDER_PAGE_SIZE;
                messageAdapter.prepend(olderMessages);
                if (!olderMessages.isEmpty()) {
                    messageLayoutManager.scrollToPositionWithOffset(
                            anchorPosition + olderMessages.size(),
                            anchorOffset
                    );
                }
            });
        });
    }

    private void showSearchDialog() {
        if (characterId.isEmpty()) {
            return;
        }
        EditText searchInput = new EditText(this);
        searchInput.setHint(R.string.search_hint);
        searchInput.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.search_messages)
                .setView(searchInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.search, (dialog, which) ->
                        searchMessages(searchInput.getText().toString())
                )
                .show();
    }

    private void searchMessages(String query) {
        if (query.trim().isEmpty()) {
            return;
        }
        databaseExecutor.execute(() -> {
            List<ChatMessage> results = historyStore.searchMessages(characterId, query, 30);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (results.isEmpty()) {
                    Toast.makeText(this, R.string.no_search_results, Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[results.size()];
                for (int index = 0; index < results.size(); index++) {
                    labels[index] = searchResultLabel(results.get(index));
                }
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.search_messages)
                        .setItems(labels, (dialog, which) -> openSearchResult(results.get(which)))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        });
    }

    private String searchResultLabel(ChatMessage message) {
        String role = message.getRole() == ChatMessage.Role.USER ? "我" : character.getName();
        String content = message.getContent().replace('\n', ' ').trim();
        if (content.isEmpty() && message.hasImageAttachment()) {
            content = "[图片]";
        }
        if (content.length() > 70) {
            content = content.substring(0, 70) + "…";
        }
        return role + "：" + content;
    }

    private void openSearchResult(ChatMessage target) {
        databaseExecutor.execute(() -> {
            List<ChatMessage> context = historyStore.loadMessageContext(
                    characterId,
                    target.getId(),
                    20
            );
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                messageAdapter.replaceAll(context);
                for (int index = 0; index < context.size(); index++) {
                    if (context.get(index).getId() == target.getId()) {
                        messageLayoutManager.scrollToPositionWithOffset(index, 120);
                        break;
                    }
                }
                hasMoreHistory = context.size() >= 21;
            });
        });
    }

    private void handlePrimaryAction() {
        if (activeStream != null) {
            stopStreaming();
            return;
        }
        sendMessage();
    }

    private void sendMessage() {
        if (character == null) {
            return;
        }
        String content = messageInput.getText().toString().trim();
        String attachmentPath = pendingAttachmentPath;
        String attachmentMimeType = pendingAttachmentMimeType;
        long replyToMessageId = pendingReplyMessage == null
                ? -1
                : pendingReplyMessage.getId();
        String replyPreviewText = pendingReplyMessage == null
                ? ""
                : buildMessageReference(pendingReplyMessage);
        if (content.isEmpty() && attachmentPath.isEmpty()) {
            return;
        }
        messageInput.setText("");
        draftStore.clear(characterId);
        clearPendingAttachment(false);
        clearPendingReply();

        long userCreatedAt = System.currentTimeMillis();
        ChatMessage userMessage = new ChatMessage(
                -1,
                ChatMessage.Role.USER,
                ChatMessage.Kind.TEXT,
                "",
                content,
                userCreatedAt,
                "",
                "",
                ChatMessage.ActionState.NONE,
                attachmentPath,
                attachmentMimeType,
                replyToMessageId,
                replyPreviewText,
                ""
        );
        messageAdapter.add(userMessage);
        scrollToLatest();
        databaseExecutor.execute(() -> {
            long id = historyStore.addMessage(
                    character.getId(),
                    userMessage.getRole(),
                    userMessage.getKind(),
                    userMessage.getTitle(),
                    userMessage.getContent(),
                    userMessage.getCreatedAt(),
                    userMessage.getActionToken(),
                    userMessage.getActionType(),
                    userMessage.getActionState(),
                    userMessage.getAttachmentPath(),
                    userMessage.getAttachmentMimeType(),
                    userMessage.getReplyToMessageId(),
                    userMessage.getReplyPreview(),
                    userMessage.getReaction()
            );
            mainHandler.post(() -> messageAdapter.assignPersistedId(
                    userCreatedAt,
                    ChatMessage.Role.USER,
                    id
            ));
        });

        AgentCard agentCard = attachmentPath.isEmpty() ? agentRouter.route(content) : null;
        if (agentCard != null) {
            addAgentCard(agentCard);
            return;
        }

        ModelSettings settings = settingsStore.load();
        if (settings.isRemoteConfigured()) {
            startStreaming(settings);
        } else {
            String mockInput = content.isEmpty() ? "我发送了一张图片。" : content;
            mainHandler.postDelayed(() -> addMockReply(mockInput), 350);
        }
    }

    private void prepareImageAttachment(Uri sourceUri) {
        if (sourceUri == null) {
            return;
        }
        attachImageButton.setEnabled(false);
        Toast.makeText(this, R.string.image_processing, Toast.LENGTH_SHORT).show();
        imageExecutor.execute(() -> {
            try {
                ImageAttachmentStore.Attachment attachment = imageAttachmentStore.importImage(sourceUri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                Bitmap previewBitmap = BitmapFactory.decodeFile(attachment.getPath(), options);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        imageAttachmentStore.delete(attachment.getPath());
                        return;
                    }
                    clearPendingAttachment(true);
                    pendingAttachmentPath = attachment.getPath();
                    pendingAttachmentMimeType = attachment.getMimeType();
                    attachmentPreviewImage.setImageBitmap(previewBitmap);
                    attachmentPreview.setVisibility(View.VISIBLE);
                    attachImageButton.setEnabled(activeStream == null);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        attachImageButton.setEnabled(activeStream == null && character != null);
                        String detail = exception.getMessage() == null
                                ? getString(R.string.image_process_failed)
                                : exception.getMessage();
                        Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void clearPendingAttachment(boolean deleteFile) {
        String pathToDelete = pendingAttachmentPath;
        pendingAttachmentPath = "";
        pendingAttachmentMimeType = "";
        attachmentPreviewImage.setImageDrawable(null);
        attachmentPreview.setVisibility(View.GONE);
        if (deleteFile && !pathToDelete.isEmpty()) {
            imageExecutor.execute(() -> imageAttachmentStore.delete(pathToDelete));
        }
    }

    private void beginReply(ChatMessage message) {
        pendingReplyMessage = message;
        replyPreviewText.setText(getString(
                R.string.replying_to,
                buildMessageReference(message)
        ));
        replyPreview.setVisibility(View.VISIBLE);
        messageInput.requestFocus();
    }

    private void clearPendingReply() {
        pendingReplyMessage = null;
        replyPreviewText.setText("");
        replyPreview.setVisibility(View.GONE);
    }

    private String buildMessageReference(ChatMessage message) {
        String content = message.getContent().replace('\n', ' ').trim();
        if (content.isEmpty() && message.hasImageAttachment()) {
            content = "[图片]";
        }
        if (content.length() > 80) {
            content = content.substring(0, 80) + "…";
        }
        String speaker = message.getRole() == ChatMessage.Role.USER
                ? "我"
                : character.getName();
        return speaker + "：" + content;
    }

    private void addAgentCard(AgentCard card) {
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
        messageAdapter.add(message);
        scrollToLatest();
        databaseExecutor.execute(() -> {
            long id = historyStore.addMessage(
                    character.getId(),
                    message.getRole(),
                    message.getKind(),
                    message.getTitle(),
                    message.getContent(),
                    message.getCreatedAt(),
                    message.getActionToken(),
                    message.getActionType(),
                    message.getActionState()
            );
            mainHandler.post(() -> messageAdapter.assignPersistedId(
                    createdAt,
                    ChatMessage.Role.ASSISTANT,
                    id
            ));
        });
        if (requiresConfirmation) {
            databaseExecutor.execute(() -> historyStore.addAgentAudit(
                    character.getId(),
                    message.getActionToken(),
                    message.getActionType(),
                    ChatMessage.ActionState.PENDING.name(),
                    message.getContent(),
                    message.getCreatedAt()
            ));
        }
    }

    private void confirmAgentAction(ChatMessage proposal) {
        messageAdapter.updateActionState(
                proposal.getActionToken(),
                ChatMessage.ActionState.CONFIRMED
        );
        if (!"clear_conversation".equals(proposal.getActionType())) {
            failAgentAction(proposal, "暂不支持该操作");
            return;
        }
        databaseExecutor.execute(() -> {
            long createdAt = System.currentTimeMillis();
            String title = "会话已清空";
            String content = "当前角色的聊天消息已删除，角色、模型设置和其他会话未受影响。";
            try {
                List<String> attachmentPaths = historyStore.loadAttachmentPaths(character.getId());
                long id = historyStore.clearConversationAndAddAgentResult(
                        character.getId(),
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
                        id,
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
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    messageAdapter.replaceAll(List.of(result));
                    scrollToLatest();
                });
            } catch (RuntimeException exception) {
                mainHandler.post(() -> failAgentAction(proposal, "执行失败，请稍后重试"));
            }
        });
    }

    private void cancelAgentAction(ChatMessage proposal) {
        messageAdapter.updateActionState(
                proposal.getActionToken(),
                ChatMessage.ActionState.CANCELLED
        );
        databaseExecutor.execute(() -> {
            historyStore.updateActionState(
                    proposal.getActionToken(),
                    ChatMessage.ActionState.CANCELLED
            );
            historyStore.addAgentAudit(
                    character.getId(),
                    proposal.getActionToken(),
                    proposal.getActionType(),
                    ChatMessage.ActionState.CANCELLED.name(),
                    "用户取消执行",
                    System.currentTimeMillis()
            );
        });
    }

    private void failAgentAction(ChatMessage proposal, String detail) {
        messageAdapter.updateActionState(
                proposal.getActionToken(),
                ChatMessage.ActionState.FAILED
        );
        databaseExecutor.execute(() -> {
            historyStore.updateActionState(
                    proposal.getActionToken(),
                    ChatMessage.ActionState.FAILED
            );
            historyStore.addAgentAudit(
                    character.getId(),
                    proposal.getActionToken(),
                    proposal.getActionType(),
                    ChatMessage.ActionState.FAILED.name(),
                    detail,
                    System.currentTimeMillis()
            );
        });
    }

    private void addMockReply(String userMessage) {
        String reply = replyEngine.reply(character, userMessage);
        addAndPersistAssistantText(reply, System.currentTimeMillis());
    }

    private void startStreaming(ModelSettings settings) {
        streamingText = new StringBuilder();
        synchronized (streamBufferLock) {
            pendingStreamDeltas.setLength(0);
            streamRenderScheduled = false;
        }
        streamingCreatedAt = System.currentTimeMillis();
        List<ChatMessage> contextWindow = messageAdapter.snapshotRecentTextMessages(20);
        messageAdapter.add(new ChatMessage(
                -1,
                ChatMessage.Role.ASSISTANT,
                getString(R.string.stream_connecting),
                streamingCreatedAt
        ));
        setStreamingUi(true);
        scrollToLatest();

        activeStream = modelClient.streamReply(
                settings,
                character,
                contextWindow,
                new OpenAiCompatibleClient.StreamListener() {
                    @Override
                    public void onOpen() {
                    }

                    @Override
                    public void onDelta(String delta) {
                        queueStreamDelta(delta);
                    }

                    @Override
                    public void onComplete() {
                        mainHandler.post(() -> {
                            mainHandler.removeCallbacks(streamRenderRunnable);
                            flushStreamDeltas();
                            completeStreaming();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        mainHandler.post(() -> failStreaming(message));
                    }
                }
        );
    }

    private void queueStreamDelta(String delta) {
        synchronized (streamBufferLock) {
            pendingStreamDeltas.append(delta);
            if (streamRenderScheduled) {
                return;
            }
            streamRenderScheduled = true;
        }
        mainHandler.postDelayed(streamRenderRunnable, 50);
    }

    private void flushStreamDeltas() {
        if (activeStream == null) {
            return;
        }
        String delta;
        synchronized (streamBufferLock) {
            delta = pendingStreamDeltas.toString();
            pendingStreamDeltas.setLength(0);
            streamRenderScheduled = false;
        }
        if (delta.isEmpty()) {
            return;
        }
        boolean shouldScroll = isNearBottom();
        streamingText.append(delta);
        messageAdapter.updateLast(new ChatMessage(
                -1,
                ChatMessage.Role.ASSISTANT,
                streamingText.toString(),
                streamingCreatedAt
        ));
        if (shouldScroll) {
            scrollToLatest();
        }
    }

    private void completeStreaming() {
        if (activeStream == null) {
            return;
        }
        String finalText = streamingText.toString().trim();
        if (finalText.isEmpty()) {
            finalText = "模型服务没有返回文本内容";
            messageAdapter.updateLast(new ChatMessage(
                    -1,
                    ChatMessage.Role.ASSISTANT,
                    finalText,
                    streamingCreatedAt
            ));
        }
        persistAssistantText(finalText, streamingCreatedAt);
        activeStream = null;
        setStreamingUi(false);
    }

    private void failStreaming(String errorMessage) {
        if (activeStream == null) {
            return;
        }
        String visibleMessage = "请求失败：" + errorMessage;
        messageAdapter.updateLast(new ChatMessage(
                -1,
                ChatMessage.Role.ASSISTANT,
                visibleMessage,
                streamingCreatedAt
        ));
        activeStream = null;
        setStreamingUi(false);
        scrollToLatest();
    }

    private void stopStreaming() {
        OpenAiCompatibleClient.StreamCall stream = activeStream;
        if (stream == null) {
            return;
        }
        stream.cancel();
        mainHandler.removeCallbacks(streamRenderRunnable);
        flushStreamDeltas();
        String partialText = streamingText.toString().trim();
        String finalText = partialText.isEmpty()
                ? getString(R.string.stream_cancelled)
                : partialText + "\n\n[已停止生成]";
        messageAdapter.updateLast(new ChatMessage(
                -1,
                ChatMessage.Role.ASSISTANT,
                finalText,
                streamingCreatedAt
        ));
        persistAssistantText(finalText, streamingCreatedAt);
        activeStream = null;
        setStreamingUi(false);
    }

    private void setStreamingUi(boolean streaming) {
        sendButton.setText(streaming ? R.string.stop : R.string.send);
        messageInput.setEnabled(character != null);
        attachImageButton.setEnabled(!streaming && character != null);
    }

    private void addAndPersistAssistantText(String content, long createdAt) {
        messageAdapter.add(new ChatMessage(
                -1,
                ChatMessage.Role.ASSISTANT,
                content,
                createdAt
        ));
        scrollToLatest();
        persistAssistantText(content, createdAt);
    }

    private void persistAssistantText(String content, long createdAt) {
        databaseExecutor.execute(() -> {
            long id = historyStore.addMessage(
                    character.getId(),
                    ChatMessage.Role.ASSISTANT,
                    content,
                    createdAt
            );
            mainHandler.post(() -> messageAdapter.assignPersistedId(
                    createdAt,
                    ChatMessage.Role.ASSISTANT,
                    id
            ));
        });
    }

    private boolean isNearBottom() {
        int itemCount = messageAdapter.getItemCount();
        if (itemCount == 0) {
            return true;
        }
        return messageLayoutManager.findLastVisibleItemPosition() >= itemCount - 2;
    }

    private void persistDraft() {
        if (!characterId.isEmpty()) {
            draftStore.save(characterId, messageInput.getText().toString());
        }
    }

    private void showMessageActions(ChatMessage message) {
        if (message.getKind() != ChatMessage.Kind.TEXT) {
            return;
        }
        if (message.getId() <= 0) {
            copyMessage(message);
            return;
        }
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.copy_message));
        actions.add(getString(R.string.reply_message));
        if (message.getRole() == ChatMessage.Role.ASSISTANT) {
            actions.add(getString(R.string.regenerate_message));
        }
        actions.add(getString(R.string.react_message));
        actions.add(getString(R.string.edit_message));
        actions.add(getString(R.string.delete_message));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.message_actions)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if (action.equals(getString(R.string.copy_message))) {
                        copyMessage(message);
                    } else if (action.equals(getString(R.string.reply_message))) {
                        beginReply(message);
                    } else if (action.equals(getString(R.string.regenerate_message))) {
                        regenerateMessage(message);
                    } else if (action.equals(getString(R.string.react_message))) {
                        showReactionPicker(message);
                    } else if (action.equals(getString(R.string.edit_message))) {
                        editMessage(message);
                    } else {
                        confirmDeleteMessage(message);
                    }
                })
                .show();
    }

    private void showReactionPicker(ChatMessage message) {
        String[] reactions = {"👍", "❤️", "😂", "😮", "😢", getString(R.string.remove_reaction)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.react_message)
                .setItems(reactions, (dialog, which) -> {
                    String reaction = which == reactions.length - 1 ? "" : reactions[which];
                    databaseExecutor.execute(() -> {
                        try {
                            historyStore.updateMessageReaction(message.getId(), reaction);
                            mainHandler.post(() -> messageAdapter.updateReaction(
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
        if (activeStream != null || message.getRole() != ChatMessage.Role.ASSISTANT) {
            return;
        }
        databaseExecutor.execute(() -> {
            ChatMessage source = historyStore.loadPreviousUserMessage(
                    characterId,
                    message.getId()
            );
            if (source == null) {
                mainHandler.post(() -> Toast.makeText(
                        this,
                        R.string.cannot_regenerate,
                        Toast.LENGTH_SHORT
                ).show());
                return;
            }
            try {
                historyStore.deleteMessage(message.getId());
            } catch (RuntimeException exception) {
                mainHandler.post(this::showMessageActionFailure);
                return;
            }
            mainHandler.post(() -> {
                messageAdapter.removeMessage(message.getId());
                ModelSettings settings = settingsStore.load();
                if (settings.isRemoteConfigured()) {
                    startStreaming(settings);
                } else {
                    String input = source.getContent().isEmpty()
                            ? "我发送了一张图片。"
                            : source.getContent();
                    addMockReply(input);
                }
            });
        });
    }

    private void copyMessage(ChatMessage message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.getContent()));
        Toast.makeText(this, R.string.message_copied, Toast.LENGTH_SHORT).show();
    }

    private void editMessage(ChatMessage message) {
        EditText editInput = new EditText(this);
        editInput.setMinLines(3);
        editInput.setMaxLines(10);
        editInput.setText(message.getContent());
        editInput.setSelection(message.getContent().length());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_message_title)
                .setView(editInput)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save_changes, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String updatedContent = editInput.getText().toString().trim();
                    if (updatedContent.isEmpty() && !message.hasImageAttachment()) {
                        Toast.makeText(this, R.string.message_cannot_be_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    databaseExecutor.execute(() -> {
                        try {
                            historyStore.updateMessageContent(message.getId(), updatedContent);
                            mainHandler.post(() -> {
                                messageAdapter.updateMessageContent(message.getId(), updatedContent);
                                Toast.makeText(this, R.string.message_updated, Toast.LENGTH_SHORT).show();
                            });
                        } catch (RuntimeException exception) {
                            mainHandler.post(this::showMessageActionFailure);
                        }
                    });
                }));
        dialog.show();
    }

    private void confirmDeleteMessage(ChatMessage message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_message_title)
                .setMessage(R.string.delete_message_description)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_message, (dialog, which) ->
                        databaseExecutor.execute(() -> {
                            try {
                                historyStore.deleteMessage(message.getId());
                                if (message.hasImageAttachment()) {
                                    imageAttachmentStore.delete(message.getAttachmentPath());
                                }
                                mainHandler.post(() -> {
                                    if (pendingReplyMessage != null
                                            && pendingReplyMessage.getId() == message.getId()) {
                                        clearPendingReply();
                                    }
                                    messageAdapter.removeMessage(message.getId());
                                    Toast.makeText(this, R.string.message_deleted, Toast.LENGTH_SHORT).show();
                                });
                            } catch (RuntimeException exception) {
                                mainHandler.post(this::showMessageActionFailure);
                            }
                        })
                )
                .show();
    }

    private void showMessageActionFailure() {
        Toast.makeText(this, R.string.message_action_failed, Toast.LENGTH_SHORT).show();
    }

    private void scrollToLatest() {
        if (messageAdapter.getItemCount() > 0) {
            messageList.scrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        persistDraft();
        if (activeStream != null) {
            activeStream.cancel();
            activeStream = null;
        }
        modelClient.close();
        clearPendingAttachment(true);
        clearPendingReply();
        messageAdapter.close();
        mainHandler.removeCallbacksAndMessages(null);
        databaseExecutor.execute(() -> {
            historyStore.close();
            characterRepository.close();
        });
        databaseExecutor.shutdown();
        imageExecutor.shutdown();
        super.onDestroy();
    }
}
