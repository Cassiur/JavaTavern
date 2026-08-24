package com.zcz.javatavern;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.zcz.javatavern.data.ChatRepository;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.ProviderCatalog;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.importer.CharacterCardParser;
import com.zcz.javatavern.importer.PngCharacterCardReader;
import com.zcz.javatavern.media.AvatarStore;
import com.zcz.javatavern.model.CharacterCardData;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.HomeFeedItem;
import com.zcz.javatavern.performance.StartupTracer;
import com.zcz.javatavern.ui.CharacterAdapter;
import com.zcz.javatavern.util.AppExecutors;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "character_id";
    private static final int MAX_CARD_BYTES = 10 * 1024 * 1024;

    private final CharacterCardParser cardParser = new CharacterCardParser();
    private AvatarStore avatarStore;
    private ChatRepository chatRepository;
    private CharacterAdapter characterAdapter;
    private ImageButton importButton;
    private EditText searchInput;
    private TextView modelStatusText;
    private View modelStatusDot;
    private List<HomeFeedItem> allFeed = List.of();
    private ActivityResultLauncher<String[]> cardPicker;
    private ActivityResultLauncher<Intent> characterEditorLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatRepository = new ChatRepository(getApplicationContext());
        avatarStore = new AvatarStore(getApplicationContext());
        characterAdapter = new CharacterAdapter(
                List.of(),
                this::openChat,
                this::editCharacter
        );
        RecyclerView characterList = findViewById(R.id.characterList);
        characterList.setLayoutManager(new LinearLayoutManager(this));
        characterList.setAdapter(characterAdapter);

        cardPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::importCharacterCard
        );
        characterEditorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadCharacters();
                    }
                }
        );

        FloatingActionButton createFab = findViewById(R.id.createCharacterFab);
        createFab.setOnClickListener(view ->
                characterEditorLauncher.launch(new Intent(this, CharacterEditorActivity.class))
        );
        importButton = findViewById(R.id.importCharacterButton);
        importButton.setOnClickListener(view -> cardPicker.launch(new String[]{
                "image/png",
                "application/json",
                "text/json",
                "text/plain"
        }));

        findViewById(R.id.settingsButton).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class))
        );
        findViewById(R.id.groupsButton).setOnClickListener(view ->
                startActivity(new Intent(this, GroupListActivity.class))
        );

        modelStatusText = findViewById(R.id.modelStatusText);
        modelStatusDot = findViewById(R.id.modelStatusDot);
        searchInput = findViewById(R.id.searchInput);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                applyFilter(text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        loadCharacters();
        StartupTracer.trackFirstDraw(this, findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (modelStatusText == null || modelStatusDot == null) {
            return;
        }
        ModelSettings settings = new SecureModelSettingsStore(this).load();
        if (settings.isRemoteConfigured()) {
            modelStatusDot.setBackgroundResource(R.drawable.bg_status_dot_online);
            modelStatusText.setText(getString(
                    R.string.model_status_online,
                    ProviderCatalog.findById(settings.getProviderId()).getDisplayName()
            ));
        } else {
            modelStatusDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            modelStatusText.setText(R.string.model_status_offline);
        }
    }

    private void loadCharacters() {
        AppExecutors.get().diskIo().execute(() -> {
            List<HomeFeedItem> feed = chatRepository.loadHomeFeed();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                allFeed = feed;
                applyFilter(searchInput.getText().toString());
            });
        });
    }

    private void applyFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            characterAdapter.replaceAll(allFeed);
            return;
        }
        List<HomeFeedItem> filtered = new ArrayList<>();
        for (HomeFeedItem item : allFeed) {
            CharacterProfile character = item.getCharacter();
            if (character.getName().toLowerCase(Locale.ROOT).contains(normalized)
                    || character.getDescription().toLowerCase(Locale.ROOT).contains(normalized)
                    || item.getPreview().toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(item);
            }
        }
        characterAdapter.replaceAll(filtered);
    }

    private void importCharacterCard(Uri uri) {
        if (uri == null) {
            return;
        }
        importButton.setEnabled(false);
        AppExecutors.get().diskIo().execute(() -> {
            try {
                byte[] bytes = readDocumentBytes(uri);
                CharacterCardData card = parseCard(bytes);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        importButton.setEnabled(true);
                        return;
                    }
                    showImportOptions(card);
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showImportFailure(exception));
            }
        });
    }

    private void showImportOptions(CharacterCardData card) {
        int worldCount = card.getWorldEntries().size();
        String[] labels = new String[]{
                getString(R.string.import_worldbook, worldCount),
                getString(R.string.import_greeting),
                getString(R.string.import_system_prompt)
        };
        boolean[] checked = new boolean[]{worldCount > 0, true, true};
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.import_options_title) + "：" + card.getName())
                .setMultiChoiceItems(labels, checked, null)
                .setPositiveButton(R.string.import_confirm, (dialog, which) -> {
                    CharacterCardData filtered = new CharacterCardData(
                            card.getName(),
                            card.getDescription(),
                            checked[1] ? card.getGreeting() : "你好。",
                            checked[2] ? card.getSystemPrompt() : "",
                            card.getSourceHash(),
                            card.getAvatar(),
                            checked[0] ? card.getWorldEntries() : List.of()
                    );
                    performImport(filtered);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> importButton.setEnabled(true))
                .show();
    }

    private void performImport(CharacterCardData card) {
        AppExecutors.get().diskIo().execute(() -> {
            try {
                CharacterProfile imported = chatRepository.importCard(card);
                List<HomeFeedItem> feed = chatRepository.loadHomeFeed();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    importButton.setEnabled(true);
                    allFeed = feed;
                    applyFilter(searchInput.getText().toString());
                    Toast.makeText(
                            this,
                            getString(R.string.character_imported, imported.getName()),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showImportFailure(exception));
            }
        });
    }

    private void showImportFailure(Exception exception) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        importButton.setEnabled(true);
        String detail = exception.getMessage() == null
                ? getString(R.string.invalid_character_card)
                : exception.getMessage();
        Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
    }

    private byte[] readDocumentBytes(Uri uri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException(getString(R.string.cannot_read_character_card));
            }
            byte[] buffer = new byte[8 * 1024];
            int totalBytes = 0;
            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1) {
                totalBytes += readBytes;
                if (totalBytes > MAX_CARD_BYTES) {
                    throw new IOException(getString(R.string.character_card_too_large));
                }
                outputStream.write(buffer, 0, readBytes);
            }
            return outputStream.toByteArray();
        }
    }

    private CharacterCardData parseCard(byte[] bytes) throws IOException {
        if (PngCharacterCardReader.isPng(bytes)) {
            String json = PngCharacterCardReader.extractCardJson(bytes);
            if (json == null) {
                throw new IOException(getString(R.string.invalid_character_card));
            }
            CharacterCardData base = parseJson(json);
            String avatarPath = avatarStore.storeFromBytes(bytes);
            return new CharacterCardData(
                    base.getName(),
                    base.getDescription(),
                    base.getGreeting(),
                    base.getSystemPrompt(),
                    base.getSourceHash(),
                    avatarPath,
                    base.getWorldEntries()
            );
        }
        return parseJson(new String(bytes, StandardCharsets.UTF_8));
    }

    private CharacterCardData parseJson(String json) throws IOException {
        try {
            return cardParser.parse(json);
        } catch (JSONException exception) {
            throw new IOException(getString(R.string.invalid_character_card), exception);
        }
    }

    private void openChat(CharacterProfile character) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(EXTRA_CHARACTER_ID, character.getId());
        startActivity(intent);
    }

    private void editCharacter(CharacterProfile character) {
        Intent intent = new Intent(this, CharacterEditorActivity.class);
        intent.putExtra(CharacterEditorActivity.EXTRA_CHARACTER_ID, character.getId());
        characterEditorLauncher.launch(intent);
    }

    @Override
    protected void onDestroy() {
        AppExecutors.get().diskIo().execute(chatRepository::close);
        super.onDestroy();
    }
}
