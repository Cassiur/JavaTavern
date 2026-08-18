package com.zcz.javatavern;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.zcz.javatavern.data.CharacterRepository;
import com.zcz.javatavern.model.CharacterProfile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CharacterEditorActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "editor_character_id";
    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private CharacterRepository characterRepository;
    private String characterId = "";
    private TextInputLayout nameLayout;
    private TextInputLayout greetingLayout;
    private EditText nameInput;
    private EditText descriptionInput;
    private EditText greetingInput;
    private EditText rulesInput;
    private MaterialButton saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_character_editor);

        characterRepository = new CharacterRepository(getApplicationContext());
        characterId = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        if (characterId == null) {
            characterId = "";
        }
        nameLayout = findViewById(R.id.characterNameLayout);
        greetingLayout = findViewById(R.id.characterGreetingLayout);
        nameInput = findViewById(R.id.characterNameInput);
        descriptionInput = findViewById(R.id.characterDescriptionInput);
        greetingInput = findViewById(R.id.characterGreetingInput);
        rulesInput = findViewById(R.id.characterRulesInput);
        saveButton = findViewById(R.id.saveCharacterButton);

        findViewById(R.id.editorBackButton).setOnClickListener(view -> finish());
        saveButton.setOnClickListener(view -> saveCharacter());
        if (!characterId.isEmpty()) {
            ((TextView) findViewById(R.id.editorTitle)).setText(R.string.edit_character_title);
            loadCharacter();
        }
    }

    private void loadCharacter() {
        saveButton.setEnabled(false);
        databaseExecutor.execute(() -> {
            CharacterProfile character = characterRepository.findById(characterId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (character == null) {
                    Toast.makeText(this, R.string.character_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                nameInput.setText(character.getName());
                descriptionInput.setText(character.getDescription());
                greetingInput.setText(character.getGreeting());
                rulesInput.setText(character.getSystemPrompt());
                saveButton.setEnabled(true);
            });
        });
    }

    private void saveCharacter() {
        nameLayout.setError(null);
        greetingLayout.setError(null);
        String name = nameInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();
        String greeting = greetingInput.getText().toString().trim();
        String rules = rulesInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.character_name_required));
            return;
        }
        if (greeting.isEmpty()) {
            greetingLayout.setError(getString(R.string.first_message_required));
            return;
        }
        if (description.isEmpty()) {
            description = getString(R.string.default_character_description);
        }
        if (rules.isEmpty()) {
            rules = description;
        }
        String finalDescription = description;
        String finalRules = rules;
        saveButton.setEnabled(false);
        databaseExecutor.execute(() -> {
            try {
                if (characterId.isEmpty()) {
                    characterRepository.createCharacter(name, finalDescription, greeting, finalRules);
                } else {
                    characterRepository.updateCharacter(
                            characterId,
                            name,
                            finalDescription,
                            greeting,
                            finalRules
                    );
                }
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.character_saved, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    Toast.makeText(this, R.string.character_save_failed, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.execute(characterRepository::close);
        databaseExecutor.shutdown();
        super.onDestroy();
    }
}
