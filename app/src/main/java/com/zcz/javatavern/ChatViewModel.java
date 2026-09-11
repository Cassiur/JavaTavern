package com.zcz.javatavern;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.network.OpenAiCompatibleClient;
import com.zcz.javatavern.stream.StreamAccumulator;
import com.zcz.javatavern.stream.StreamSession;
import com.zcz.javatavern.util.AppExecutors;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the remote-stream lifecycle across Activity configuration changes.
 * Each request has a stable operation identity, ignores stale callbacks, and
 * publishes immutable snapshots while persistence runs on the shared disk
 * executor.
 */
public final class ChatViewModel extends AndroidViewModel {

    /**
     * Immutable snapshot published to observers on every delta flush and
     * terminal transition.  Null means no stream has ever started.
     */
    public static final class StreamSnapshot {
        public final StreamAccumulator.State accState;
        /**
         * Canonical display text.  For STREAMING equals accState.text.
         * For COMPLETED/STOPPED the final text with any stopped marker /
         * empty-fallback applied.  For ERROR the error message.
         */
        public final String displayText;
        /**
         * Negative while the DB write is pending; becomes the inserted row ID
         * once the background write completes.
         */
        public final long rowId;

        /**
         * 本轮流式要替换的目标消息 id（重 roll 时为该消息的 id）；普通新回复为 {@code -1}。
         * 观察者据此把流式内容渲染到原文位置，而不是追加到列表末尾。
         */
        public final long targetMessageId;

        StreamSnapshot(StreamAccumulator.State accState, String displayText, long rowId,
                long targetMessageId) {
            this.accState = accState;
            this.displayText = displayText;
            this.rowId = rowId;
            this.targetMessageId = targetMessageId;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Application-context repository — lives for the whole ViewModel lifetime. */
    private final ChatRepository ownedRepository;
    private final OpenAiCompatibleClient modelClient = new OpenAiCompatibleClient();

    /** One active session per request; null means idle. */
    private final AtomicReference<StreamSession> currentSession = new AtomicReference<>(null);

    /**
     * 当前请求的群聊发言者；单聊为 {@code null}。
     * 决定终态持久化走 {@code addMessage} 还是带 speaker 的 {@code addGroupMessage}。
     */
    private volatile CharacterProfile activeGroupSpeaker;

    /**
     * 当前请求要重 roll 的目标消息 id；新建回复为 {@code -1}。
     * 终态持久化时会追加为该消息的一个新版本，而不是新插入一条消息。
     */
    private volatile long activeRegenerateMessageId = -1L;

    private final MutableLiveData<StreamSnapshot> streamState = new MutableLiveData<>(null);

    /**
     * Token object used for all flush Runnables so cancelFlush() can remove
     * them by token reference.  Using a dedicated Object (not 'this') is
     * required because Handler.removeCallbacksAndMessages(token) only removes
     * messages whose obj field equals the token — postDelayed(Runnable, delay)
     * stores the Runnable internally as the callback, not as the obj field, so
     * the correct cancellation approach is to post with an explicit token via
     * Handler.postAtTime or to keep a stored Runnable reference.
     *
     * We keep a stored per-session Runnable: scheduleFlush captures it in a
     * local variable and cancelFlush removes it by reference.
     */
    // Flush runnable is stored per call in scheduleFlush; see cancelFlush.
    private Runnable pendingFlushRunnable;
    private final Object flushLock = new Object();

    public ChatViewModel(@NonNull Application application) {
        super(application);
        ownedRepository = new ChatRepository(application.getApplicationContext());
    }

    public LiveData<StreamSnapshot> getStreamState() {
        return streamState;
    }

    @MainThread
    public boolean isStreaming() {
        StreamSession s = currentSession.get();
        return s != null && !s.currentState().isTerminal();
    }

    /**
     * Starts a remote stream.  Main-thread only.
     * Returns false without side-effects if a non-terminal session is active.
     */
    @MainThread
    public boolean startStreaming(
            @NonNull ModelSettings settings,
            @NonNull CharacterProfile character,
            @NonNull List<ChatMessage> context,
            @NonNull String memoryPrompt
    ) {
        return startStream(
                settings,
                character.getId(),
                null,
                -1L,
                listener -> modelClient.streamReply(
                        settings, character, context, memoryPrompt, listener
                )
        );
    }

    /**
     * 重 roll：重新生成 {@code regenerateMessageId} 这条消息，结果作为它的新版本保存。
     *
     * <p>旧内容不会被删除，用户可以在气泡上左右翻回之前的版本。
     */
    @MainThread
    public boolean startRegeneration(
            @NonNull ModelSettings settings,
            @NonNull CharacterProfile character,
            long regenerateMessageId,
            @NonNull List<ChatMessage> context,
            @NonNull String memoryPrompt
    ) {
        return startStream(
                settings,
                character.getId(),
                null,
                regenerateMessageId,
                listener -> modelClient.streamReply(
                        settings, character, context, memoryPrompt, listener
                )
        );
    }

    /**
     * 群聊请求：{@code groupId} 作为会话 id（历史按群存储），{@code speaker} 为本轮发言角色。
     *
     * <p>群聊与单聊共用同一套流式生命周期，因此旋转屏幕、切后台再回来都不会丢流，
     * 也不再需要 Activity 自己维护一份 SSE 逻辑。
     */
    @MainThread
    public boolean startGroupStreaming(
            @NonNull ModelSettings settings,
            @NonNull String groupId,
            @NonNull List<CharacterProfile> members,
            @NonNull CharacterProfile speaker,
            @NonNull List<ChatMessage> context,
            @NonNull String memoryPrompt
    ) {
        return startStream(
                settings,
                groupId,
                speaker,
                -1L,
                listener -> modelClient.streamGroupReply(
                        settings, members, speaker, context, memoryPrompt, listener
                )
        );
    }

    /** 发起一次请求的抽象：单聊走 streamReply，群聊走 streamGroupReply。 */
    private interface RequestStarter {
        OpenAiCompatibleClient.StreamCall start(OpenAiCompatibleClient.StreamListener listener);
    }

    @MainThread
    private boolean startStream(
            @NonNull ModelSettings settings,
            @NonNull String sessionCharacterId,
            CharacterProfile groupSpeaker,
            long regenerateMessageId,
            @NonNull RequestStarter starter
    ) {
        StreamSession existing = currentSession.get();
        if (existing != null && !existing.currentState().isTerminal()) {
            return false;
        }

        final long opId = System.nanoTime();
        final long createdAt = System.currentTimeMillis();
        final String charId = sessionCharacterId;
        activeGroupSpeaker = groupSpeaker;
        activeRegenerateMessageId = regenerateMessageId;

        final String emptyFallback = getString(R.string.stream_empty_fallback);
        final String stoppedMarker = getString(R.string.stream_stopped_marker);
        final String errorPrefix   = getString(R.string.stream_error_prefix);

        // Pass a no-op persister: buildAsyncListener owns the single persist path.
        // This prevents any possibility of double-writing if StreamSession.persistOnce
        // and the listener both fire.
        StreamSession session = new StreamSession(
                opId, createdAt, charId,
                (cid, text, ts) -> -1L, // no-op — ViewModel listener persists instead
                null
        );
        currentSession.set(session);

        streamState.setValue(new StreamSnapshot(
                session.currentState(), getString(R.string.stream_connecting), -1L,
                regenerateMessageId));

        StreamSession.ResultListener listener = buildAsyncListener(session);

        OpenAiCompatibleClient.StreamCall call = starter.start(
                new OpenAiCompatibleClient.StreamListener() {
                    @Override public void onOpen() { }

                    @Override
                    public void onDelta(String delta) {
                        if (currentSession.get() != session) return;
                        session.appendDelta(delta);
                        scheduleFlush(session);
                    }

                    @Override
                    public void onComplete() {
                        mainHandler.post(() -> {
                            if (currentSession.get() != session) return;
                            cancelFlush();
                            publishLiveIfActive(session);
                            session.complete(listener, emptyFallback);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        mainHandler.post(() -> {
                            if (currentSession.get() != session) return;
                            cancelFlush();
                            session.error(errorPrefix + (errorMessage == null ? "" : errorMessage),
                                    listener);
                        });
                    }
                }
        );

        session.setCallHandle(call::cancel);
        return true;
    }

    @MainThread
    public void stopStreaming() {
        StreamSession session = currentSession.get();
        if (session == null || session.currentState().isTerminal()) {
            return;
        }
        session.cancel();
        cancelFlush();
        publishLiveIfActive(session);

        final String cancelledText = getString(R.string.stream_cancelled);
        final String stoppedMarker = getString(R.string.stream_stopped_marker);
        session.stop(buildAsyncListener(session), cancelledText, stoppedMarker);
    }

    // -------------------------------------------------------------------------
    // Async listener — single persist path
    // -------------------------------------------------------------------------

    /**
     * The sole path that writes to the database.
     * onTerminalText fires on the main thread (called inside mainHandler.post),
     * then dispatches the actual I/O to dbExecutor and posts the rowId back.
     */
    private StreamSession.ResultListener buildAsyncListener(StreamSession session) {
        return new StreamSession.ResultListener() {
            @Override
            public void onTerminalText(long opId, String text, long createdAt,
                    StreamAccumulator.Status status) {
                StreamAccumulator.State s = session.currentState();
                streamState.setValue(new StreamSnapshot(
                        s, text, -1L, activeRegenerateMessageId));

                if (!s.shouldPersist()) {
                    return;
                }
                final String cid = session.getCharacterId();
                final CharacterProfile speaker = activeGroupSpeaker;
                final long targetId = activeRegenerateMessageId;
                AppExecutors.get().diskIo().execute(() -> {
                    long rowId;
                    if (speaker != null) {
                        rowId = ownedRepository.addGroupMessage(
                                cid, ChatMessage.Role.ASSISTANT, text, createdAt,
                                speaker.getId(), speaker.getName());
                    } else if (targetId > 0) {
                        // 重 roll：内容作为新版本写回原消息，位置不变、旧版本保留。
                        ownedRepository.appendMessageVersion(targetId, text, createdAt);
                        rowId = targetId;
                    } else {
                        rowId = ownedRepository.addMessage(
                                cid, ChatMessage.Role.ASSISTANT, text, createdAt);
                    }
                    mainHandler.post(() -> {
                        StreamSnapshot cur = streamState.getValue();
                        if (cur != null && cur.accState.operationId == opId) {
                            streamState.setValue(new StreamSnapshot(
                                    cur.accState, cur.displayText, rowId, targetId));
                        }
                    });
                });
            }

            @Override
            public void onPersisted(long opId, long rowId, long createdAt) {
                // Unused: rowId delivered directly in onTerminalText above.
            }
        };
    }

    // -------------------------------------------------------------------------
    // 50-ms batch flush
    // -------------------------------------------------------------------------

    private void scheduleFlush(StreamSession session) {
        synchronized (flushLock) {
            if (pendingFlushRunnable != null) return; // already scheduled
            Runnable r = () -> {
                synchronized (flushLock) { pendingFlushRunnable = null; }
                if (currentSession.get() != session) return;
                publishLiveIfActive(session);
            };
            pendingFlushRunnable = r;
            mainHandler.postDelayed(r, 50);
        }
    }

    @MainThread
    private void cancelFlush() {
        Runnable r;
        synchronized (flushLock) {
            r = pendingFlushRunnable;
            pendingFlushRunnable = null;
        }
        if (r != null) {
            mainHandler.removeCallbacks(r);
        }
    }

    @MainThread
    private void publishLiveIfActive(StreamSession session) {
        StreamAccumulator.State s = session.currentState();
        if (!s.isTerminal()) {
            streamState.setValue(new StreamSnapshot(
                    s, s.text, -1L, activeRegenerateMessageId));
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCleared() {
        StreamSession s = currentSession.getAndSet(null);
        if (s != null) {
            s.cancel();
        }
        cancelFlush();
        modelClient.close();
        ownedRepository.close();
    }

    private String getString(int resId) {
        return getApplication().getString(resId);
    }
}
