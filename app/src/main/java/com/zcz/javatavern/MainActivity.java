package com.zcz.javatavern;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zcz.javatavern.data.CharacterRepository;
import com.zcz.javatavern.data.ModelSettings;
import com.zcz.javatavern.data.ProviderCatalog;
import com.zcz.javatavern.data.SecureModelSettingsStore;
import com.zcz.javatavern.importer.CharacterCardParser;
import com.zcz.javatavern.model.CharacterCardData;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.performance.StartupTracer;
import com.zcz.javatavern.ui.CharacterAdapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "character_id";
    private static final int MAX_CARD_BYTES = 2 * 1024 * 1024;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final CharacterCardParser cardParser = new CharacterCardParser();
    private CharacterRepository characterRepository;
    private CharacterAdapter characterAdapter;
    private MaterialButton importButton;
    private MaterialButton settingsButton;
    private ActivityResultLauncher<String[]> cardPicker;
    private ActivityResultLauncher<Intent> characterEditorLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        characterRepository = new CharacterRepository(getApplicationContext());
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
        findViewById(R.id.createCharacterButton).setOnClickListener(view ->
                characterEditorLauncher.launch(new Intent(this, CharacterEditorActivity.class))
        );
        importButton = findViewById(R.id.importCharacterButton);
        importButton.setOnClickListener(view -> cardPicker.launch(new String[]{
                "application/json",
                "text/json",
                "text/plain"
        }));

        settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class))
        );
        loadCharacters();
        StartupTracer.trackFirstDraw(this, findViewById(android.R.id.content));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsButton == null) {
            return;
        }
        ModelSettings settings = new SecureModelSettingsStore(this).load();
        if (settings.isRemoteConfigured()) {
            settingsButton.setText(getString(
                    R.string.model_connection_active,
                    ProviderCatalog.findById(settings.getProviderId()).getDisplayName()
            ));
        } else {
            settingsButton.setText(R.string.model_connection);
        }
    }

    private void loadCharacters() {
        databaseExecutor.execute(() -> {
            List<CharacterProfile> characters = characterRepository.getCharacters();
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    characterAdapter.replaceAll(characters);
                }
            });
        });
    }

    private void importCharacterCard(Uri uri) {
        if (uri == null) {
            return;
        }
        importButton.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                String json = readDocument(uri);
                CharacterCardData card = cardParser.parse(json);
                CharacterProfile imported = characterRepository.importCard(card);
                List<CharacterProfile> characters = characterRepository.getCharacters();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    importButton.setEnabled(true);
                    characterAdapter.replaceAll(characters);
                    Toast.makeText(
                            this,
                            getString(R.string.character_imported, imported.getName()),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    importButton.setEnabled(true);
                    String detail = exception.getMessage() == null
                            ? getString(R.string.invalid_character_card)
                            : exception.getMessage();
                    Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String readDocument(Uri uri) throws IOException {
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
            return outputStream.toString(StandardCharsets.UTF_8.name());
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
        databaseExecutor.execute(characterRepository::close);
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
