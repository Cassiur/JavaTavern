package com.zcz.javatavern.model;

import java.util.Comparator;

/**
 * A single row on the home screen: a character plus the most recent thing that
 * happened in their conversation, so the home page can answer "继续哪段故事？"
 * instead of showing a bare character list.
 *
 * <p>{@code preview} is the latest assistant/user message text, or the
 * character greeting when no conversation exists yet. {@code lastActivityAt}
 * is the {@code created_at} of that message, or 0 when there is none.
 */
public final class HomeFeedItem {
    /**
     * Sorts by recent activity descending. Characters without any messages
     * (lastActivityAt == 0) sink to the bottom and keep their relative order.
     */
    public static final Comparator<HomeFeedItem> BY_RECENT =
            Comparator.comparingLong(HomeFeedItem::getLastActivityAt).reversed();

    private final CharacterProfile character;
    private final String preview;
    private final long lastActivityAt;

    public HomeFeedItem(CharacterProfile character, String preview, long lastActivityAt) {
        this.character = character;
        this.preview = preview;
        this.lastActivityAt = lastActivityAt;
    }

    public CharacterProfile getCharacter() {
        return character;
    }

    public String getPreview() {
        return preview;
    }

    public long getLastActivityAt() {
        return lastActivityAt;
    }
}
