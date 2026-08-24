package com.zcz.javatavern.ui;

import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.zcz.javatavern.R;
import com.zcz.javatavern.model.CharacterProfile;
import com.zcz.javatavern.model.HomeFeedItem;
import com.zcz.javatavern.util.RelativeTime;

import java.util.List;

public final class CharacterAdapter extends RecyclerView.Adapter<CharacterAdapter.CharacterViewHolder> {
    public interface OnCharacterClickListener {
        void onCharacterClick(CharacterProfile character);
    }

    public interface OnCharacterEditListener {
        void onCharacterEdit(CharacterProfile character);
    }

    private static final String RESOURCE_PREFIX = "res:";
    private final List<HomeFeedItem> items;
    private final OnCharacterClickListener listener;
    private final OnCharacterEditListener editListener;

    public CharacterAdapter(
            List<HomeFeedItem> items,
            OnCharacterClickListener listener,
            OnCharacterEditListener editListener
    ) {
        this.items = new java.util.ArrayList<>(items);
        this.listener = listener;
        this.editListener = editListener;
    }

    public void replaceAll(List<HomeFeedItem> newItems) {
        List<HomeFeedItem> oldItems = new java.util.ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).getCharacter().getId()
                        .equals(newItems.get(newItemPosition).getCharacter().getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                HomeFeedItem oldItem = oldItems.get(oldItemPosition);
                HomeFeedItem newItem = newItems.get(newItemPosition);
                CharacterProfile oldCharacter = oldItem.getCharacter();
                CharacterProfile newCharacter = newItem.getCharacter();
                return oldCharacter.getName().equals(newCharacter.getName())
                        && oldCharacter.getDescription().equals(newCharacter.getDescription())
                        && oldCharacter.getAccentColor() == newCharacter.getAccentColor()
                        && oldCharacter.getAvatar().equals(newCharacter.getAvatar())
                        && oldItem.getPreview().equals(newItem.getPreview())
                        && oldItem.getLastActivityAt() == newItem.getLastActivityAt();
            }
        });
        items.clear();
        items.addAll(newItems);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public CharacterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_character, parent, false);
        return new CharacterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CharacterViewHolder holder, int position) {
        HomeFeedItem item = items.get(position);
        CharacterProfile character = item.getCharacter();
        holder.name.setText(character.getName());
        holder.preview.setText(item.getPreview());
        String relativeTime = RelativeTime.format(item.getLastActivityAt(), System.currentTimeMillis());
        holder.time.setText(relativeTime);
        holder.time.setVisibility(relativeTime.isEmpty() ? View.GONE : View.VISIBLE);
        bindWorldbookTag(holder, character);
        bindPortrait(holder, character);
        holder.itemView.setOnClickListener(view -> listener.onCharacterClick(character));
        holder.editButton.setOnClickListener(view -> editListener.onCharacterEdit(character));
    }

    private void bindWorldbookTag(CharacterViewHolder holder, CharacterProfile character) {
        int count = character.getWorldEntries().size();
        if (count > 0) {
            holder.tag.setText(holder.itemView.getContext().getString(
                    R.string.character_tag_worldbook, count));
            holder.tag.setVisibility(View.VISIBLE);
        } else {
            holder.tag.setVisibility(View.GONE);
        }
    }

    private void bindPortrait(CharacterViewHolder holder, CharacterProfile character) {
        String avatar = character.getAvatar();
        if (avatar == null || avatar.isEmpty()) {
            renderInitialFallback(holder, character);
            return;
        }
        if (avatar.startsWith(RESOURCE_PREFIX)) {
            String name = avatar.substring(RESOURCE_PREFIX.length());
            int resId = holder.itemView.getResources().getIdentifier(
                    name, "drawable", holder.itemView.getContext().getPackageName());
            if (resId != 0) {
                holder.portrait.setImageResource(resId);
                holder.portrait.setVisibility(View.VISIBLE);
                holder.initialAvatar.setVisibility(View.GONE);
                return;
            }
        } else {
            // Treat as absolute file path (imported PNG card portrait).
            if (android.graphics.BitmapFactory.decodeFile(avatar) != null
                    || new java.io.File(avatar).exists()) {
                holder.portrait.setImageBitmap(BitmapFactory.decodeFile(avatar));
                holder.portrait.setVisibility(View.VISIBLE);
                holder.initialAvatar.setVisibility(View.GONE);
                return;
            }
        }
        renderInitialFallback(holder, character);
    }

    private void renderInitialFallback(CharacterViewHolder holder, CharacterProfile character) {
        holder.portrait.setImageDrawable(null);
        holder.portrait.setVisibility(View.GONE);
        holder.initialAvatar.setText(character.getName().substring(0, 1));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(character.getAccentColor());
        holder.initialAvatar.setBackground(background);
        holder.initialAvatar.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class CharacterViewHolder extends RecyclerView.ViewHolder {
        private final TextView initialAvatar;
        private final ImageView portrait;
        private final TextView name;
        private final TextView tag;
        private final TextView preview;
        private final TextView time;
        private final View editButton;

        CharacterViewHolder(@NonNull View itemView) {
            super(itemView);
            initialAvatar = itemView.findViewById(R.id.characterAvatar);
            portrait = itemView.findViewById(R.id.characterPortrait);
            name = itemView.findViewById(R.id.characterName);
            tag = itemView.findViewById(R.id.characterTag);
            preview = itemView.findViewById(R.id.characterPreview);
            time = itemView.findViewById(R.id.characterTime);
            editButton = itemView.findViewById(R.id.editCharacterButton);
        }
    }
}
