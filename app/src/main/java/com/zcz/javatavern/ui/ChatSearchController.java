package com.zcz.javatavern.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zcz.javatavern.R;
import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.model.ChatMessage;
import com.zcz.javatavern.util.AppExecutors;

import java.util.List;
import java.util.function.Supplier;

/**
 * Extracted from {@code ChatActivity}: in-session message search.
 *
 * <p>Search is a self-contained concern — it only reads the repository and
 * mutates the adapter / scroll position, so it lives behind two narrow
 * callbacks ({@link Listener}) instead of reaching back into the Activity.
 */
public final class ChatSearchController {

    public interface Listener {
        /** Host Activity is alive and safe to touch UI on. */
        boolean isHostActive();

        /** Called when opening a search result resets the history window. */
        void onSearchContextOpened(boolean hasMoreHistory);
    }

    private static final int SEARCH_LIMIT = 30;
    private static final int CONTEXT_RADIUS = 20;

    private final Context context;
    private final ChatRepository repository;
    private final MessageAdapter adapter;
    private final LinearLayoutManager layoutManager;
    private final RecyclerView messageList;
    private final Listener listener;
    private final Supplier<String> characterId;
    private final Supplier<String> characterName;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ChatSearchController(
            Context context,
            ChatRepository repository,
            MessageAdapter adapter,
            LinearLayoutManager layoutManager,
            RecyclerView messageList,
            Listener listener,
            Supplier<String> characterId,
            Supplier<String> characterName
    ) {
        this.context = context;
        this.repository = repository;
        this.adapter = adapter;
        this.layoutManager = layoutManager;
        this.messageList = messageList;
        this.listener = listener;
        this.characterId = characterId;
        this.characterName = characterName;
    }

    public void showSearchDialog() {
        String id = characterId.get();
        if (id.isEmpty()) {
            return;
        }
        EditText searchInput = new EditText(context);
        searchInput.setHint(R.string.search_hint);
        searchInput.setSingleLine(true);
        new MaterialAlertDialogBuilder(context)
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
        String id = characterId.get();
        AppExecutors.get().diskIo().execute(() -> {
            List<ChatMessage> results = repository.searchMessages(id, query, SEARCH_LIMIT);
            mainHandler.post(() -> {
                if (!listener.isHostActive()) {
                    return;
                }
                if (results.isEmpty()) {
                    Toast.makeText(context, R.string.no_search_results, Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] labels = new String[results.size()];
                for (int index = 0; index < results.size(); index++) {
                    labels[index] = searchResultLabel(results.get(index));
                }
                new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.search_messages)
                        .setItems(labels, (dialog, which) -> openSearchResult(results.get(which)))
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            });
        });
    }

    private void openSearchResult(ChatMessage target) {
        String id = characterId.get();
        AppExecutors.get().diskIo().execute(() -> {
            List<ChatMessage> context = repository.loadMessageContext(id, target.getId(), CONTEXT_RADIUS);
            mainHandler.post(() -> {
                if (!listener.isHostActive()) {
                    return;
                }
                adapter.replaceAll(context);
                for (int index = 0; index < context.size(); index++) {
                    if (context.get(index).getId() == target.getId()) {
                        layoutManager.scrollToPositionWithOffset(index, 120);
                        break;
                    }
                }
                listener.onSearchContextOpened(context.size() >= CONTEXT_RADIUS + 1);
            });
        });
    }

    private String searchResultLabel(ChatMessage message) {
        String role = message.getRole() == ChatMessage.Role.USER
                ? context.getString(R.string.speaker_user)
                : characterName.get();
        String content = message.getContent().replace('\n', ' ').trim();
        if (content.isEmpty() && message.hasImageAttachment()) {
            content = context.getString(R.string.image_placeholder);
        }
        if (content.length() > 70) {
            content = content.substring(0, 70) + "…";
        }
        return role + "：" + content;
    }
}
