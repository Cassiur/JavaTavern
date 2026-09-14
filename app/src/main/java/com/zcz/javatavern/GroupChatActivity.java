package com.zcz.javatavern;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zcz.javatavern.data.CharacterRepository;
import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.data.GroupRepository;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.model.Group;
import com.zcz.javatavern.ui.MessageAdapter;
import com.zcz.javatavern.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * 群聊聊天页。
 *
 * <p>流式生命周期复用 {@link ChatViewModel}（与单聊同一套），因此旋转屏幕或切后台
 * 再回来都不会丢流，也不再需要在这里维护一份 SSE 逻辑。本页只负责：加载群聊与成员、
 * 选择本轮发言者、把流式快照渲染成带 speaker 标记的气泡。
 */
public final class GroupChatActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "group_chat_group_id";

    private GroupRepository groupRepository;
    private CharacterRepository characterRepository;
    private ChatRepository chatRepository;
    private SecureModelSettingsStore settingsStore;

    private ChatViewModel chatViewModel;
    private MessageAdapter messageAdapter;
    private Spinner speakerSpinner;
    private EditText messageInput;
    private MaterialButton sendButton;

    private String groupId = "";
    private List<CharacterProfile> members = List.of();
    private boolean streaming = false;
    /** 本轮请求对应的发言者与时间戳，用于渲染带 speaker 的流式气泡。 */
    private CharacterProfile pendingSpeaker;
    private long streamCreatedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        groupRepository = new GroupRepository(getApplicationContext());
        characterRepository = new CharacterRepository(getApplicationContext());
        chatRepository = new ChatRepository(getApplicationContext());
        settingsStore = new SecureModelSettingsStore(this);
        chatViewModel = new ViewModelProvider(this).get(ChatViewModel.class);

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
                message -> { } // 群聊消息长按暂无操作
        );
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(messageAdapter);

        speakerSpinner = findViewById(R.id.groupSpeakerSpinner);
        messageInput = findViewById(R.id.groupMessageInput);
        sendButton = findViewById(R.id.groupSendButton);

        findViewById(R.id.groupChatBackButton).setOnClickListener(view -> finish());
        sendButton.setOnClickListener(view -> handlePrimaryAction());

        chatViewModel.getStreamState().observe(this, this::applyStreamSnapshot);
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
                // 旋转恢复后把 ViewModel 里仍在进行的流式内容重新渲染出来。
                ChatViewModel.StreamSnapshot retained = chatViewModel.getStreamState().getValue();
                if (retained != null) {
                    applyStreamSnapshot(retained);
                } else {
                    setStreamingUi(chatViewModel.isStreaming());
                }
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

    private void handlePrimaryAction() {
        if (chatViewModel.isStreaming()) {
            chatViewModel.stopStreaming();
            return;
        }
        sendMessage();
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

        // 先放一个空的流式气泡（带 speaker），后续由快照驱动更新。
        streamCreatedAt = createdAt + 1;
        pendingSpeaker = speaker;
        messageAdapter.add(new ChatMessage(
                -1, ChatMessage.Role.ASSISTANT, "", streamCreatedAt, speaker.getName()));
        messageInput.setText("");

        List<ChatMessage> context = messageAdapter.snapshotRecentTextMessages(200);
        boolean started = chatViewModel.startGroupStreaming(
                settings,
                groupId,
                members,
                speaker,
                context,
                ""
        );
        setStreamingUi(started);
    }

    private void applyStreamSnapshot(ChatViewModel.StreamSnapshot snapshot) {
        if (snapshot == null) {
            setStreamingUi(false);
            return;
        }
        String speakerName = pendingSpeaker == null ? "" : pendingSpeaker.getName();
        String text = snapshot.displayText;
        ChatMessage row = new ChatMessage(
                snapshot.rowId > 0 ? snapshot.rowId : -1,
                ChatMessage.Role.ASSISTANT,
                text,
                streamCreatedAt,
                speakerName
        );
        if (messageAdapter.getItemCount() == 0) {
            messageAdapter.add(row);
        } else {
            messageAdapter.updateLast(row);
        }
        setStreamingUi(!snapshot.accState.isTerminal());
    }

    private void setStreamingUi(boolean active) {
        streaming = active;
        sendButton.setText(active ? R.string.stop : R.string.send);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回到前台时，如果 ViewModel 仍在流式中，恢复按钮状态与最近快照。
        ChatViewModel.StreamSnapshot retained = chatViewModel.getStreamState().getValue();
        if (retained != null) {
            applyStreamSnapshot(retained);
        }
    }
}
