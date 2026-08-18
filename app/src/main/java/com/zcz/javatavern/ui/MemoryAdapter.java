package com.zcz.javatavern.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.zcz.javatavern.R;
import com.zcz.javatavern.model.MemoryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MemoryAdapter extends RecyclerView.Adapter<MemoryAdapter.MemoryViewHolder> {
    private final List<MemoryEntry> entries = new ArrayList<>();
    private final Consumer<MemoryEntry> deleteListener;

    public MemoryAdapter(Consumer<MemoryEntry> deleteListener) {
        this.deleteListener = deleteListener;
    }

    public void replaceAll(List<MemoryEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_memory, parent, false);
        return new MemoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemoryViewHolder holder, int position) {
        MemoryEntry entry = entries.get(position);
        holder.content.setText(entry.getContent());
        holder.delete.setOnClickListener(view -> deleteListener.accept(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static final class MemoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView content;
        private final MaterialButton delete;

        MemoryViewHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.memoryContent);
            delete = itemView.findViewById(R.id.deleteMemoryButton);
        }
    }
}
