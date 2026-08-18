package com.zcz.javatavern.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;

import com.zcz.javatavern.R;
import com.zcz.javatavern.model.CharacterProfile;

import java.util.List;

public final class CharacterAdapter extends RecyclerView.Adapter<CharacterAdapter.CharacterViewHolder> {
    public interface OnCharacterClickListener {
        void onCharacterClick(CharacterProfile character);
    }

    public interface OnCharacterEditListener {
        void onCharacterEdit(CharacterProfile character);
    }

    private final List<CharacterProfile> characters;
    private final OnCharacterClickListener listener;
    private final OnCharacterEditListener editListener;

    public CharacterAdapter(
            List<CharacterProfile> characters,
            OnCharacterClickListener listener,
            OnCharacterEditListener editListener
    ) {
        this.characters = new java.util.ArrayList<>(characters);
        this.listener = listener;
        this.editListener = editListener;
    }

    public void replaceAll(List<CharacterProfile> newCharacters) {
        List<CharacterProfile> oldCharacters = new java.util.ArrayList<>(characters);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldCharacters.size();
            }

            @Override
            public int getNewListSize() {
                return newCharacters.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldCharacters.get(oldItemPosition).getId()
                        .equals(newCharacters.get(newItemPosition).getId());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                CharacterProfile oldCharacter = oldCharacters.get(oldItemPosition);
                CharacterProfile newCharacter = newCharacters.get(newItemPosition);
                return oldCharacter.getName().equals(newCharacter.getName())
                        && oldCharacter.getDescription().equals(newCharacter.getDescription())
                        && oldCharacter.getAccentColor() == newCharacter.getAccentColor();
            }
        });
        characters.clear();
        characters.addAll(newCharacters);
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
        CharacterProfile character = characters.get(position);
        holder.name.setText(character.getName());
        holder.description.setText(character.getDescription());
        holder.avatar.setText(character.getName().substring(0, 1));

        GradientDrawable avatarBackground = new GradientDrawable();
        avatarBackground.setShape(GradientDrawable.OVAL);
        avatarBackground.setColor(character.getAccentColor());
        holder.avatar.setBackground(avatarBackground);
        holder.itemView.setOnClickListener(view -> listener.onCharacterClick(character));
        holder.editButton.setOnClickListener(view -> editListener.onCharacterEdit(character));
    }

    @Override
    public int getItemCount() {
        return characters.size();
    }

    static final class CharacterViewHolder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final TextView name;
        private final TextView description;
        private final View editButton;

        CharacterViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.characterAvatar);
            name = itemView.findViewById(R.id.characterName);
            description = itemView.findViewById(R.id.characterDescription);
            editButton = itemView.findViewById(R.id.editCharacterButton);
        }
    }
}
