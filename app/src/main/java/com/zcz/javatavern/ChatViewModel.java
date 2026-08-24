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

        StreamSnapshot(StreamAccumulator.State accState, String displayText, long rowId) {
            this.accState = accState;
            this.displayText = displayText;
            this.rowId = rowId;
        }
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** Application-context repository — lives for the whole ViewModel lifetime. */
    private final ChatRepository ownedRepository;
    private final OpenAiCompatibleClient modelClient = new OpenAiCompatibleClient();

    /** One active session per request; null means idle. */
    private final AtomicReference<StreamSession> currentSession = new AtomicReference<>(null);

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
        StreamSession existing = currentSession.get();
        if (existing != null && !existing.currentState().isTerminal()) {
            return false;
        }

        final long opId = System.nanoTime();
        final long createdAt = System.currentTimeMillis();
        final String charId = character.getId();

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
                session.currentState(), getString(R.string.stream_connecting), -1L));

        StreamSession.ResultListener listener = buildAsyncListener(session);

        OpenAiCompatibleClient.StreamCall call = modelClient.streamReply(
                settings, character, context, memoryPrompt,
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
                streamState.setValue(new StreamSnapshot(s, text, -1L));

                if (!s.shouldPersist()) {
                    return;
                }
                final String cid = session.getCharacterId();
                AppExecutors.get().diskIo().execute(() -> {
                    long rowId = ownedRepository.addMessage(
                            cid, ChatMessage.Role.ASSISTANT, text, createdAt);
                    mainHandler.post(() -> {
                        StreamSnapshot cur = streamState.getValue();
                        if (cur != null && cur.accState.operationId == opId) {
                            streamState.setValue(new StreamSnapshot(
                                    cur.accState, cur.displayText, rowId));
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
            streamState.setValue(new StreamSnapshot(s, s.text, -1L));
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
