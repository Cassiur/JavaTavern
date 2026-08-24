package com.zcz.javatavern.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.zcz.javatavern.R;
import com.zcz.javatavern.model.WorldBookEntry;

import java.util.ArrayList;
import java.util.List;

public final class WorldBookEntryAdapter
        extends RecyclerView.Adapter<WorldBookEntryAdapter.EntryViewHolder> {

    public interface EntryActionListener {
        void onEditEntry(WorldBookEntry entry);
    }

    private final List<WorldBookEntry> entries = new ArrayList<>();
    private final EntryActionListener listener;

    public WorldBookEntryAdapter(EntryActionListener listener) {
        this.listener = listener;
    }

    public void replaceAll(List<WorldBookEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_world_entry, parent, false);
        return new EntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        WorldBookEntry entry = entries.get(position);
        boolean before = entry.getPosition() == WorldBookEntry.POSITION_BEFORE_CHAR;
        holder.positionTag.setText(before
                ? holder.itemView.getContext().getString(R.string.world_position_before)
                : holder.itemView.getContext().getString(R.string.world_position_after));
        holder.keywords.setText(String.join(", ", entry.getKeywords()));
        holder.content.setText(entry.getContent());
        holder.disabledTag.setVisibility(entry.isEnabled() ? View.GONE : View.VISIBLE);
        holder.meta.setText(holder.itemView.getContext().getString(
                R.string.world_entry_meta,
                entry.getOrder(),
                entry.getProbability()
        ));
        holder.itemView.setOnClickListener(view -> listener.onEditEntry(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static final class EntryViewHolder extends RecyclerView.ViewHolder {
        final TextView positionTag;
        final TextView keywords;
        final TextView content;
        final TextView disabledTag;
        final TextView meta;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            positionTag = itemView.findViewById(R.id.worldEntryPositionTag);
            keywords = itemView.findViewById(R.id.worldEntryKeywords);
            content = itemView.findViewById(R.id.worldEntryContent);
            disabledTag = itemView.findViewById(R.id.worldEntryDisabledTag);
            meta = itemView.findViewById(R.id.worldEntryMeta);
        }
    }
}
