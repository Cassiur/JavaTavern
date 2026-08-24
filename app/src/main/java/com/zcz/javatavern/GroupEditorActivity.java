package com.zcz.javatavern;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.zcz.javatavern.data.CharacterRepository;
import com.zcz.javatavern.data.GroupRepository;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public final class GroupEditorActivity extends AppCompatActivity {
    private GroupRepository groupRepository;
    private CharacterRepository characterRepository;
    private TextInputLayout nameLayout;
    private ListView memberList;
    private List<CharacterProfile> characters = List.of();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_editor);

        groupRepository = new GroupRepository(getApplicationContext());
        characterRepository = new CharacterRepository(getApplicationContext());
        nameLayout = findViewById(R.id.groupNameLayout);
        memberList = findViewById(R.id.groupMemberList);

        findViewById(R.id.groupEditorBackButton).setOnClickListener(view -> finish());
        findViewById(R.id.createGroupButton).setOnClickListener(view -> createGroup());

        loadCharacters();
    }

    private void loadCharacters() {
        AppExecutors.get().diskIo().execute(() -> {
            List<CharacterProfile> loaded = characterRepository.getCharacters();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                characters = loaded;
                ArrayAdapter<String> adapter = new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_multiple_choice,
                        names(loaded)
                );
                memberList.setAdapter(adapter);
            });
        });
    }

    private List<String> names(List<CharacterProfile> profiles) {
        List<String> names = new ArrayList<>(profiles.size());
        for (CharacterProfile profile : profiles) {
            names.add(profile.getName());
        }
        return names;
    }

    private void createGroup() {
        nameLayout.setError(null);
        String name = ((android.widget.EditText) findViewById(R.id.groupNameInput))
                .getText().toString().trim();
        if (name.isEmpty()) {
            nameLayout.setError(getString(R.string.group_name_required));
            return;
        }
        List<String> selectedIds = new ArrayList<>();
        for (int index = 0; index < characters.size(); index++) {
            if (memberList.isItemChecked(index)) {
                selectedIds.add(characters.get(index).getId());
            }
        }
        if (selectedIds.size() < 2) {
            Toast.makeText(this, R.string.group_min_members, Toast.LENGTH_SHORT).show();
            return;
        }
        AppExecutors.get().diskIo().execute(() -> {
            try {
                groupRepository.createGroup(name, selectedIds);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    Toast.makeText(this, R.string.group_created, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> Toast.makeText(
                        this, R.string.group_create_failed, Toast.LENGTH_LONG).show());
            }
        });
    }
}
