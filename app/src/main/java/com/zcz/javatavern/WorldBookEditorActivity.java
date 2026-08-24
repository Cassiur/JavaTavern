package com.zcz.javatavern;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.zcz.javatavern.data.CharacterRepository;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.WorldBookEntry;
import com.zcz.javatavern.ui.WorldBookEntryAdapter;
import com.zcz.javatavern.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public final class WorldBookEditorActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "worldbook_character_id";

    private CharacterRepository repository;
    private String characterId = "";
    private String characterName = "";
    private WorldBookEntryAdapter adapter;
    private TextView emptyHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_worldbook_editor);

        repository = new CharacterRepository(getApplicationContext());
        characterId = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        if (characterId == null) {
            characterId = "";
        }

        emptyHint = findViewById(R.id.worldBookEmptyHint);
        RecyclerView list = findViewById(R.id.worldEntryList);
        adapter = new WorldBookEntryAdapter(this::showEntryDialog);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.worldBookBackButton).setOnClickListener(view -> finish());
        findViewById(R.id.addWorldEntryFab).setOnClickListener(view -> showEntryDialog(null));

        loadWorldBook();
    }

    private void loadWorldBook() {
        AppExecutors.get().diskIo().execute(() -> {
            CharacterProfile character = repository.findById(characterId);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (character == null) {
                    Toast.makeText(this, R.string.character_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                characterName = character.getName();
                ((TextView) findViewById(R.id.worldBookSubtitle)).setText(characterName);
                renderEntries(character.getWorldEntries());
            });
        });
    }

    private void renderEntries(List<WorldBookEntry> entries) {
        adapter.replaceAll(entries);
        emptyHint.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showEntryDialog(WorldBookEntry existing) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_world_entry, null);
        TextInputLayout keywordsLayout = dialogView.findViewById(R.id.worldKeywordsLayout);
        TextInputLayout contentLayout = dialogView.findViewById(R.id.worldContentLayout);
        TextInputLayout orderLayout = dialogView.findViewById(R.id.worldOrderLayout);
        TextInputLayout probabilityLayout = dialogView.findViewById(R.id.worldProbabilityLayout);
        EditText keywordsInput = dialogView.findViewById(R.id.worldKeywordsInput);
        EditText contentInput = dialogView.findViewById(R.id.worldContentInput);
        EditText orderInput = dialogView.findViewById(R.id.worldOrderInput);
        EditText priorityInput = dialogView.findViewById(R.id.worldPriorityInput);
        EditText probabilityInput = dialogView.findViewById(R.id.worldProbabilityInput);
        Spinner positionSpinner = dialogView.findViewById(R.id.worldPositionSpinner);
        SwitchCompat enabledSwitch = dialogView.findViewById(R.id.worldEnabledSwitch);
        SwitchCompat constantSwitch = dialogView.findViewById(R.id.worldConstantSwitch);

        ArrayAdapter<String> positionAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{
                        getString(R.string.world_position_before),
                        getString(R.string.world_position_after)
                }
        );
        positionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        positionSpinner.setAdapter(positionAdapter);

        boolean editing = existing != null;
        if (editing) {
            keywordsInput.setText(String.join(", ", existing.getKeywords()));
            contentInput.setText(existing.getContent());
            orderInput.setText(String.valueOf(existing.getOrder()));
            priorityInput.setText(String.valueOf(existing.getPriority()));
            probabilityInput.setText(String.valueOf(existing.getProbability()));
            positionSpinner.setSelection(existing.getPosition() == WorldBookEntry.POSITION_BEFORE_CHAR ? 0 : 1);
            enabledSwitch.setChecked(existing.isEnabled());
            constantSwitch.setChecked(existing.isConstant());
        } else {
            orderInput.setText(String.valueOf(WorldBookEntry.DEFAULT_ORDER));
            probabilityInput.setText(String.valueOf(WorldBookEntry.DEFAULT_PROBABILITY));
            enabledSwitch.setChecked(true);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(editing ? R.string.edit_world_entry : R.string.add_world_entry)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, null);
        if (editing) {
            builder.setNeutralButton(R.string.delete_preset,
                    (dialogInterface, which) -> confirmDeleteEntry(existing));
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    keywordsLayout.setError(null);
                    contentLayout.setError(null);
                    orderLayout.setError(null);
                    probabilityLayout.setError(null);

                    List<String> keywords = parseKeywords(keywordsInput.getText().toString());
                    if (keywords.isEmpty()) {
                        keywordsLayout.setError(getString(R.string.world_keywords_required));
                        return;
                    }
                    String content = contentInput.getText().toString().trim();
                    if (content.isEmpty()) {
                        contentLayout.setError(getString(R.string.world_content_required));
                        return;
                    }
                    int order = parseIntOrDefault(
                            orderInput, orderLayout, WorldBookEntry.DEFAULT_ORDER, 0);
                    if (order < 0) {
                        return;
                    }
                    int priority = parseNonNegative(priorityInput, 0);
                    int probability = parseIntOrDefault(
                            probabilityInput, probabilityLayout,
                            WorldBookEntry.DEFAULT_PROBABILITY, 0);
                    if (probability < 0 || probability > 100) {
                        probabilityLayout.setError(getString(R.string.world_probability_range));
                        return;
                    }
                    boolean constant = constantSwitch.isChecked();
                    int position = positionSpinner.getSelectedItemPosition() == 0
                            ? WorldBookEntry.POSITION_BEFORE_CHAR
                            : WorldBookEntry.POSITION_AFTER_CHAR;
                    WorldBookEntry entry = new WorldBookEntry(
                            existing == null ? 0L : existing.getId(),
                            keywords,
                            content,
                            enabledSwitch.isChecked(),
                            constant,
                            position,
                            order,
                            priority,
                            existing == null ? WorldBookEntry.DEFAULT_DEPTH : existing.getDepth(),
                            probability,
                            existing != null && existing.isExcludeRecursion(),
                            existing != null && existing.isPreventRecursion()
                    );
                    dialog.dismiss();
                    persistEntry(entry, editing);
                }));
        dialog.show();
    }

    private void confirmDeleteEntry(WorldBookEntry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_preset)
                .setMessage(R.string.world_entry_delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_preset, (dialog, which) ->
                        AppExecutors.get().diskIo().execute(() -> {
                            try {
                                repository.deleteWorldEntry(entry.getId());
                                runOnUiThread(this::loadWorldBook);
                            } catch (RuntimeException exception) {
                                runOnUiThread(() -> Toast.makeText(
                                        this,
                                        R.string.world_entry_save_failed,
                                        Toast.LENGTH_LONG
                                ).show());
                            }
                        }))
                .show();
    }

    private void persistEntry(WorldBookEntry entry, boolean editing) {        AppExecutors.get().diskIo().execute(() -> {
            try {
                if (editing) {
                    repository.updateWorldEntry(entry.getId(), entry);
                } else {
                    repository.addWorldEntry(characterId, entry);
                }
                runOnUiThread(this::loadWorldBook);
            } catch (RuntimeException exception) {
                runOnUiThread(() -> Toast.makeText(
                        this, R.string.world_entry_save_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private List<String> parseKeywords(String raw) {
        List<String> keywords = new ArrayList<>();
        for (String part : raw.split("[,，]")) {
            String keyword = part.trim();
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private int parseIntOrDefault(EditText input, TextInputLayout layout, int defaultValue, int min) {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < min) {
                layout.setError(getString(R.string.world_invalid_number));
                return -1;
            }
            return value;
        } catch (NumberFormatException exception) {
            layout.setError(getString(R.string.world_invalid_number));
            return -1;
        }
    }

    private int parseNonNegative(EditText input, int defaultValue) {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }
}
