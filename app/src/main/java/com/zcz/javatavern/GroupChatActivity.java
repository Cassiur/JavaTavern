package com.zcz.javatavern;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zcz.javatavern.data.CharacterRepository;
import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.data.GroupRepository;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.Group;
import com.zcz.javatavern.network.OpenAiCompatibleClient;
import com.zcz.javatavern.ui.MessageAdapter;
import com.zcz.javatavern.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public final class GroupChatActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "group_chat_group_id";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GroupRepository groupRepository;
    private CharacterRepository characterRepository;
    private ChatRepository chatRepository;
    private SecureModelSettingsStore settingsStore;
    private OpenAiCompatibleClient modelClient;

    private MessageAdapter messageAdapter;
    private Spinner speakerSpinner;
    private EditText messageInput;
    private String groupId = "";
    private List<CharacterProfile> members = List.of();
    private boolean streaming = false;
    private long activeOperationId = 0L;
    private OpenAiCompatibleClient.StreamCall activeStream;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        groupRepository = new GroupRepository(getApplicationContext());
        characterRepository = new CharacterRepository(getApplicationContext());
        chatRepository = new ChatRepository(getApplicationContext());
        settingsStore = new SecureModelSettingsStore(this);
        modelClient = new OpenAiCompatibleClient();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        if (groupId == null) {
            groupId = "";
        }

        RecyclerView list = findViewById(R.id.groupMessageList);
        messageAdapter = new MessageAdapter(
                new MessageAdapter.AgentActionListener() {
                    @Override public void onConfirm(ChatMessage message) { }
                    @Override public void onCancel(ChatMessage message) { }
                },
                message -> { } // MVP：群聊消息长按暂无操作
        );
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(messageAdapter);

        speakerSpinner = findViewById(R.id.groupSpeakerSpinner);
        messageInput = findViewById(R.id.groupMessageInput);

        findViewById(R.id.groupChatBackButton).setOnClickListener(view -> finish());
        findViewById(R.id.groupSendButton).setOnClickListener(view -> sendMessage());

        loadGroup();
    }

    private void loadGroup() {
        AppExecutors.get().diskIo().execute(() -> {
            Group group = groupRepository.findGroup(groupId);
            List<CharacterProfile> loadedMembers = loadMembers(group);
            List<ChatMessage> messages = group == null
                    ? List.of()
                    : chatRepository.loadGroupMessages(group.getId());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (group == null) {
                    Toast.makeText(this, R.string.group_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                ((TextView) findViewById(R.id.groupChatTitle)).setText(group.getName());
                members = loadedMembers;
                bindSpeakerSpinner();
                messageAdapter.replaceAll(messages);
            });
        });
    }

    private List<CharacterProfile> loadMembers(Group group) {
        if (group == null) {
            return List.of();
        }
        List<CharacterProfile> profiles = new ArrayList<>();
        for (String memberId : group.getMemberIds()) {
            CharacterProfile profile = characterRepository.findById(memberId);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    private void bindSpeakerSpinner() {
        List<String> names = new ArrayList<>();
        for (CharacterProfile member : members) {
            names.add(member.getName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        speakerSpinner.setAdapter(adapter);
        if (!names.isEmpty()) {
            speakerSpinner.setSelection(0);
        }
    }

    private CharacterProfile currentSpeaker() {
        int index = speakerSpinner.getSelectedItemPosition();
        if (index < 0 || index >= members.size()) {
            return null;
        }
        return members.get(index);
    }

    private void sendMessage() {
        if (streaming) {
            return;
        }
        String content = messageInput.getText().toString().trim();
        if (content.isEmpty()) {
            return;
        }
        ModelSettings settings = settingsStore.load();
        if (!settings.isRemoteConfigured()) {
            Toast.makeText(this, R.string.group_needs_model, Toast.LENGTH_LONG).show();
            return;
        }
        CharacterProfile speaker = currentSpeaker();
        if (speaker == null) {
            Toast.makeText(this, R.string.group_min_members, Toast.LENGTH_SHORT).show();
            return;
        }

        long createdAt = System.currentTimeMillis();
        messageAdapter.add(new ChatMessage(-1, ChatMessage.Role.USER, content, createdAt));
        AppExecutors.get().diskIo().execute(() -> chatRepository.addGroupMessage(
                groupId, ChatMessage.Role.USER, content, createdAt, "", ""));
        messageAdapter.add(new ChatMessage(
                -1, ChatMessage.Role.ASSISTANT, "", createdAt + 1, speaker.getName()));
        messageInput.setText("");
        streaming = true;
        long operationId = ++activeOperationId;

        List<ChatMessage> context = messageAdapter.snapshotRecentTextMessages(20);
        StringBuilder acc = new StringBuilder();
        activeStream = modelClient.streamGroupReply(
                settings,
                members,
                speaker,
                context,
                "",
                new OpenAiCompatibleClient.StreamListener() {
                    @Override public void onOpen() { }

                    @Override
                    public void onDelta(String delta) {
                        acc.append(delta);
                        String snapshot = acc.toString();
                        mainHandler.post(() -> {
                            if (!isActive(operationId)) {
                                return;
                            }
                            messageAdapter.updateLast(new ChatMessage(
                                    -1, ChatMessage.Role.ASSISTANT, snapshot,
                                    createdAt + 1, speaker.getName()));
                        });
                    }

                    @Override
                    public void onComplete() {
                        mainHandler.post(() -> {
                            if (!isActive(operationId)) {
                                return;
                            }
                            String text = acc.toString().trim();
                            if (text.isEmpty()) {
                                text = getString(R.string.stream_empty_fallback);
                            }
                            String finalText = text;
                            messageAdapter.updateLast(new ChatMessage(
                                    -1, ChatMessage.Role.ASSISTANT, finalText,
                                    createdAt + 1, speaker.getName()));
                            streaming = false;
                            activeStream = null;
                            AppExecutors.get().diskIo().execute(() -> {
                                long rowId = chatRepository.addGroupMessage(
                                        groupId, ChatMessage.Role.ASSISTANT, finalText,
                                        createdAt + 1, speaker.getId(), speaker.getName());
                                mainHandler.post(() -> {
                                    if (!isFinishing() && !isDestroyed()) {
                                        messageAdapter.assignPersistedId(
                                                createdAt + 1, ChatMessage.Role.ASSISTANT, rowId);
                                    }
                                });
                            });
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        mainHandler.post(() -> {
                            if (!isActive(operationId)) {
                                return;
                            }
                            messageAdapter.updateLast(new ChatMessage(
                                    -1, ChatMessage.Role.ASSISTANT,
                                    getString(R.string.stream_error_prefix)
                                            + (errorMessage == null ? "" : errorMessage),
                                    createdAt + 1, speaker.getName()));
                            streaming = false;
                            activeStream = null;
                        });
                    }
                }
        );
    }

    private boolean isActive(long operationId) {
        return operationId == activeOperationId && !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onDestroy() {
        activeOperationId++;
        if (activeStream != null) {
            activeStream.cancel();
            activeStream = null;
        }
        modelClient.close();
        super.onDestroy();
    }
}
