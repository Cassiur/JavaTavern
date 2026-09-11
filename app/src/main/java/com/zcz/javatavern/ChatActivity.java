package com.zcz.javatavern;

import android.content.Intent;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zcz.javatavern.agent.LocalAgentRouter;
import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.data.ConversationDraftStore;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.media.ImageAttachmentStore;
import com.zcz.javatavern.model.AgentCard;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.service.MockReplyEngine;
import com.zcz.javatavern.service.ReplyEngine;
import com.zcz.javatavern.stream.StreamAccumulator;
import com.zcz.javatavern.ui.ChatAgentController;
import com.zcz.javatavern.ui.ChatMessageActionsController;
import com.zcz.javatavern.ui.ChatSearchController;
import com.zcz.javatavern.ui.MessageAdapter;
import com.zcz.javatavern.util.AppExecutors;

import java.util.List;

public final class ChatActivity extends AppCompatActivity {
    private static final int INITIAL_PAGE_SIZE = 60;
    private static final int OLDER_PAGE_SIZE = 40;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ReplyEngine replyEngine = new MockReplyEngine();
    private final LocalAgentRouter agentRouter = new LocalAgentRouter();

    // Stream lifecycle is owned by the ViewModel — do NOT keep modelClient here.
    private ChatViewModel chatViewModel;

    private CharacterProfile character;
    private String characterId = "";
    private ChatRepository chatRepository;
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
    // Stream snapshots are reconciled only after persisted history is loaded.
    private boolean historyLoaded;
    private final Runnable persistDraftRunnable = this::persistDraft;
    private boolean restoringDraft;
    private View offlineBanner;
    private MaterialButton offlineBannerAction;

    // Focused collaborators keep search, agent actions, and message actions separate.
    private ChatSearchController searchController;
    private ChatAgentController agentController;
    private ChatMessageActionsController messageActionsController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        String requestedCharacterId = getIntent().getStringExtra(MainActivity.EXTRA_CHARACTER_ID);
        chatRepository = new ChatRepository(getApplicationContext());
        draftStore = new ConversationDraftStore(getApplicationContext());
        settingsStore = new SecureModelSettingsStore(getApplicationContext());
        imageAttachmentStore = new ImageAttachmentStore(getApplicationContext());

        TextView title = findViewById(R.id.chatTitle);
        title.setText(R.string.loading_character);
        findViewById(R.id.backButton).setOnClickListener(view -> finish());

        messageList = findViewById(R.id.messageList);
        messageLayoutManager = new LinearLayoutManager(this);
        messageLayoutManager.setStackFromEnd(true);

        // The adapter's listeners forward to the collaborators below; the lambda
        // bodies resolve the fields lazily (they are non-null by first click).
        messageAdapter = new MessageAdapter(
                new MessageAdapter.AgentActionListener() {
                    @Override
                    public void onConfirm(ChatMessage message) {
                        agentController.confirm(message);
                    }

                    @Override
                    public void onCancel(ChatMessage message) {
                        agentController.cancel(message);
                    }
                },
                message -> messageActionsController.showActions(message)
        );

        searchController = new ChatSearchController(
                this,
                chatRepository,
                messageAdapter,
                messageLayoutManager,
                messageList,
                new ChatSearchController.Listener() {
                    @Override
                    public boolean isHostActive() {
                        return isHostActive();
                    }

                    @Override
                    public void onSearchContextOpened(boolean more) {
                        hasMoreHistory = more;
                    }
                },
                () -> characterId,
                () -> character == null ? "" : character.getName()
        );
        agentController = new ChatAgentController(
                this,
                chatRepository,
                messageAdapter,
                imageAttachmentStore,
                new ChatAgentController.Listener() {
                    @Override
                    public boolean isHostActive() {
                        return isHostActive();
                    }

                    @Override
                    public void scrollToLatest() {
                        ChatActivity.this.scrollToLatest();
                    }
                },
                () -> characterId
        );
        messageActionsController = new ChatMessageActionsController(
                this,
                chatRepository,
                messageAdapter,
                settingsStore,
                new ChatMessageActionsController.Listener() {
                    @Override
                    public boolean isHostActive() {
                        return isHostActive();
                    }

                    @Override
                    public boolean isStreaming() {
                        return chatViewModel.isStreaming();
                    }

                    @Override
                    public void onBeginReply(ChatMessage message) {
                        beginReply(message);
                    }

                    @Override
                    public void onRegenerate(ModelSettings settings) {
                        startStreaming(settings);
                    }

                    @Override
                    public void onMockReply(String input) {
                        addMockReply(input);
                    }

                    @Override
                    public void onClearPendingReplyIfMatches(long messageId) {
                        if (pendingReplyMessage != null && pendingReplyMessage.getId() == messageId) {
                            clearPendingReply();
                        }
                    }

                    @Override
                    public void onDeleteImageAttachment(String path) {
                        imageAttachmentStore.delete(path);
                    }
                },
                () -> characterId,
                () -> character == null ? "" : character.getName()
        );

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
        offlineBanner = findViewById(R.id.offlineBanner);
        offlineBannerAction = findViewById(R.id.offlineBannerAction);
        offlineBannerAction.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class))
        );
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
        findViewById(R.id.searchMessagesButton).setOnClickListener(view -> searchController.showSearchDialog());
        findViewById(R.id.memoryButton).setOnClickListener(view -> openMemory());
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

        // Observe stream state from ViewModel — survives rotation.
        chatViewModel.getStreamState().observe(this, this::applyStreamSnapshot);

        loadHistory(requestedCharacterId == null ? "" : requestedCharacterId, title);
    }

    private boolean isHostActive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Offline demo banner: only visible when no remote model is configured.
        // Refreshes on resume so returning from Settings hides it immediately.
        ModelSettings settings = settingsStore.load();
        boolean offline = !settings.isRemoteConfigured();
        if (offlineBanner != null) {
            offlineBanner.setVisibility(offline ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Renders a StreamSnapshot emitted by the ViewModel.
     *
     * Rendering waits for persisted history, uses a stable transient-row
     * identity, and immediately reflects terminal display text.
     */
    private void applyStreamSnapshot(ChatViewModel.StreamSnapshot snapshot) {
        if (snapshot == null) {
            setStreamingUi(false);
            return;
        }
        StreamAccumulator.State s = snapshot.accState;
        switch (s.status) {
            case CONNECTING:
                if (historyLoaded) {
                    messageAdapter.upsertStreamRow(s.operationId,
                            getString(R.string.stream_connecting), s.createdAt);
                    if (isNearBottom()) scrollToLatest();
                }
                setStreamingUi(true);
                break;
            case STREAMING:
                if (historyLoaded) {
                    messageAdapter.upsertStreamRow(s.operationId,
                            snapshot.displayText, s.createdAt);
                    if (isNearBottom()) scrollToLatest();
                }
                setStreamingUi(true);
                break;
            case COMPLETED:
            case STOPPED:
                if (historyLoaded) {
                    messageAdapter.upsertStreamRow(s.operationId,
                            snapshot.displayText, s.createdAt);
                    if (snapshot.rowId > 0) {
                        messageAdapter.assignPersistedId(
                                s.createdAt, ChatMessage.Role.ASSISTANT, snapshot.rowId);
                    }
                    scrollToLatest();
                }
                setStreamingUi(false);
                break;
            case ERROR:
                if (historyLoaded) {
                    messageAdapter.upsertStreamRow(s.operationId,
                            snapshot.displayText, s.createdAt);
                    scrollToLatest();
                }
                setStreamingUi(false);
                break;
        }
    }

    private void loadHistory(String requestedCharacterId, TextView title) {
        AppExecutors.get().diskIo().execute(() -> {
            ChatRepository.SessionData session = chatRepository.loadSession(
                    requestedCharacterId,
                    INITIAL_PAGE_SIZE
            );
            CharacterProfile resultCharacter = session.getCharacter();
            List<ChatMessage> messages = session.getMessages();
            mainHandler.post(() -> {
                if (!isHostActive()) {
                    return;
                }
                character = resultCharacter;
                this.characterId = character.getId();
                hasMoreHistory = session.hasMoreHistory();
                title.setText(character.getName());
                messageAdapter.replaceAll(messages);
                historyLoaded = true;
                ChatViewModel.StreamSnapshot retained =
                        chatViewModel.getStreamState().getValue();
                if (retained != null) {
                    applyStreamSnapshot(retained);
                }
                String draft = draftStore.load(characterId);
                restoringDraft = true;
                messageInput.setText(draft);
                messageInput.setSelection(draft.length());
                restoringDraft = false;
                messageInput.setEnabled(true);
                sendButton.setEnabled(true);
                attachImageButton.setEnabled(!chatViewModel.isStreaming());
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
        AppExecutors.get().diskIo().execute(() -> {
            List<ChatMessage> olderMessages = chatRepository.loadMessagesBefore(
                    characterId,
                    beforeId,
                    OLDER_PAGE_SIZE
            );
            mainHandler.post(() -> {
                loadingOlderMessages = false;
                if (!isHostActive()) {
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

    private void openMemory() {
        if (character == null) {
            return;
        }
        startActivity(new Intent(this, MemoryActivity.class)
                .putExtra(MemoryActivity.EXTRA_CHARACTER_ID, character.getId())
                .putExtra(MemoryActivity.EXTRA_CHARACTER_NAME, character.getName()));
    }

    private void handlePrimaryAction() {
        if (chatViewModel.isStreaming()) {
            chatViewModel.stopStreaming();
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
                : messageActionsController.buildReference(pendingReplyMessage);
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
        AppExecutors.get().diskIo().execute(() -> {
            long id = chatRepository.addMessage(
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
            agentController.addCard(agentCard);
            return;
        }

        ModelSettings settings = settingsStore.load();
        if (settings.isRemoteConfigured()) {
            startStreaming(settings);
        } else {
            String mockInput = content.isEmpty() ? getString(R.string.mock_image_input) : content;
            mainHandler.postDelayed(() -> addMockReply(mockInput), 350);
        }
    }

    private void startStreaming(ModelSettings settings) {
        // 只取一个足够大的候选窗口，最终按 token 预算在请求层截断。
        List<ChatMessage> contextWindow = messageAdapter.snapshotRecentTextMessages(200);
        String memoryPrompt = chatRepository.buildConfirmedMemoryPrompt(characterId);
        boolean started = chatViewModel.startStreaming(
                settings,
                character,
                contextWindow,
                memoryPrompt
        );
        if (!started) {
            return; // rejected — already streaming
        }
    }

    private void prepareImageAttachment(Uri sourceUri) {
        if (sourceUri == null) {
            return;
        }
        attachImageButton.setEnabled(false);
        Toast.makeText(this, R.string.image_processing, Toast.LENGTH_SHORT).show();
        AppExecutors.get().image().execute(() -> {
            try {
                ImageAttachmentStore.Attachment attachment = imageAttachmentStore.importImage(sourceUri);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 4;
                Bitmap previewBitmap = BitmapFactory.decodeFile(attachment.getPath(), options);
                mainHandler.post(() -> {
                    if (!isHostActive()) {
                        imageAttachmentStore.delete(attachment.getPath());
                        return;
                    }
                    clearPendingAttachment(true);
                    pendingAttachmentPath = attachment.getPath();
                    pendingAttachmentMimeType = attachment.getMimeType();
                    attachmentPreviewImage.setImageBitmap(previewBitmap);
                    attachmentPreview.setVisibility(View.VISIBLE);
                    attachImageButton.setEnabled(!chatViewModel.isStreaming());
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    if (isHostActive()) {
                        attachImageButton.setEnabled(!chatViewModel.isStreaming() && character != null);
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
            AppExecutors.get().image().execute(() -> imageAttachmentStore.delete(pathToDelete));
        }
    }

    private void beginReply(ChatMessage message) {
        pendingReplyMessage = message;
        replyPreviewText.setText(getString(
                R.string.replying_to,
                messageActionsController.buildReference(message)
        ));
        replyPreview.setVisibility(View.VISIBLE);
        messageInput.requestFocus();
    }

    private void clearPendingReply() {
        pendingReplyMessage = null;
        replyPreviewText.setText("");
        replyPreview.setVisibility(View.GONE);
    }

    private void addMockReply(String userMessage) {
        String reply = replyEngine.reply(character, userMessage);
        addAndPersistAssistantText(reply, System.currentTimeMillis());
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
        AppExecutors.get().diskIo().execute(() -> {
            long id = chatRepository.addMessage(
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

    private void scrollToLatest() {
        if (messageAdapter.getItemCount() > 0) {
            messageList.scrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        persistDraft();
        // Do NOT cancel the stream or close modelClient here — the ViewModel
        // owns that lifecycle. If this is a configuration change, onCleared()
        // will not run and the stream continues in the ViewModel.
        // If the user is genuinely leaving, onCleared() will cancel it.
        clearPendingAttachment(true);
        clearPendingReply();
        messageAdapter.close();
        mainHandler.removeCallbacksAndMessages(null);
        AppExecutors.get().diskIo().execute(chatRepository::close);
        super.onDestroy();
    }
}
