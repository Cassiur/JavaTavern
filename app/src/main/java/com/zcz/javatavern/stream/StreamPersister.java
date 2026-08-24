package com.zcz.javatavern.stream;

/**
 * Injectable persistence boundary for the stream lifecycle.
 *
 * Separates the ViewModel from the Android-framework ChatRepository so the
 * pure-Java StreamSession can be tested without an Android runtime.
 */
public interface StreamPersister {
    /**
     * Writes an assistant message to persistent storage and returns its row ID.
     * Called from a background thread. Must be safe to call from any thread.
     *
     * @param characterId  the character whose conversation receives the message
     * @param text         the final assistant text to persist
     * @param createdAt    the original creation timestamp of the stream
     * @return             the newly inserted database row ID
     */
    long persist(String characterId, String text, long createdAt);
}
