package com.zcz.javatavern;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.zcz.javatavern.data.GroupRepository;
import com.zcz.javatavern.model.Group;
import com.zcz.javatavern.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

public final class GroupListActivity extends AppCompatActivity {
    private GroupRepository groupRepository;
    private ListView groupList;
    private TextView emptyHint;
    private List<Group> groups = List.of();
    private ActivityResultLauncher<Intent> editorLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_list);

        groupRepository = new GroupRepository(getApplicationContext());
        groupList = findViewById(R.id.groupList);
        emptyHint = findViewById(R.id.groupListEmptyHint);

        findViewById(R.id.groupListBackButton).setOnClickListener(view -> finish());
        findViewById(R.id.newGroupButton).setOnClickListener(view ->
                editorLauncher.launch(new Intent(this, GroupEditorActivity.class)));
        groupList.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= groups.size()) {
                return;
            }
            Intent intent = new Intent(this, GroupChatActivity.class);
            intent.putExtra(GroupChatActivity.EXTRA_GROUP_ID, groups.get(position).getId());
            startActivity(intent);
        });

        editorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadGroups();
                    }
                }
        );

        loadGroups();
    }

    private void loadGroups() {
        AppExecutors.get().diskIo().execute(() -> {
            List<Group> loaded = groupRepository.listGroups();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                groups = loaded;
                List<String> labels = new ArrayList<>();
                for (Group group : loaded) {
                    labels.add(getString(R.string.group_list_item,
                            group.getName(), group.getMemberIds().size()));
                }
                groupList.setAdapter(new ArrayAdapter<>(
                        this, android.R.layout.simple_list_item_1, labels));
                emptyHint.setVisibility(loaded.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (groupRepository != null) {
            loadGroups();
        }
    }
}
