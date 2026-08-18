package com.zcz.javatavern;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.zcz.javatavern.memory.LongTermMemoryStore;
import com.zcz.javatavern.model.MemoryEntry;
import com.zcz.javatavern.ui.MemoryAdapter;

public final class MemoryActivity extends AppCompatActivity {
    public static final String EXTRA_CHARACTER_ID = "character_id";
    public static final String EXTRA_CHARACTER_NAME = "character_name";

    private String characterId;
    private LongTermMemoryStore memoryStore;
    private MemoryAdapter memoryAdapter;
    private EditText memoryInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memory);

        characterId = getIntent().getStringExtra(EXTRA_CHARACTER_ID);
        String characterName = getIntent().getStringExtra(EXTRA_CHARACTER_NAME);
        if (characterId == null || characterId.isEmpty()) {
            finish();
            return;
        }
        memoryStore = new LongTermMemoryStore(getApplicationContext());
        memoryInput = findViewById(R.id.memoryInput);
        TextView title = findViewById(R.id.memoryTitle);
        title.setText(getString(R.string.memory_for_character, characterName == null ? "" : characterName));
        findViewById(R.id.memoryBackButton).setOnClickListener(view -> finish());

        memoryAdapter = new MemoryAdapter(this::confirmDelete);
        RecyclerView list = findViewById(R.id.memoryList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(memoryAdapter);
        MaterialButton addButton = findViewById(R.id.addMemoryButton);
        addButton.setOnClickListener(view -> addMemory());
        refresh();
    }

    private void addMemory() {
        try {
            memoryStore.add(characterId, memoryInput.getText().toString());
            memoryInput.setText("");
            refresh();
        } catch (RuntimeException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(MemoryEntry entry) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_memory_title)
                .setMessage(entry.getContent())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_message, (dialog, which) -> {
                    memoryStore.delete(characterId, entry.getId());
                    refresh();
                })
                .show();
    }

    private void refresh() {
        memoryAdapter.replaceAll(memoryStore.load(characterId));
    }
}
