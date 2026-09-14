package com.zcz.javatavern.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.zcz.javatavern.model.ChatMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

/**
 * Exercises {@link ChatHistoryStore} against Robolectric's real SQLite, not
 * mocks — this is the largest source file in the project (736 lines) and,
 * before this round, had zero automated coverage.
 */
@RunWith(RobolectricTestRunner.class)
public final class ChatHistoryStoreTest {
    private static final String CHARACTER_ID = "test-character";

    private ChatHistoryStore store;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        store = new ChatHistoryStore(context);
    }

    @After
    public void tearDown() {
        TavernDatabase.resetSingletonForTest();
    }

    @Test
    public void loadMessagesBefore_keysetPagination_excludesBoundaryAndOrdersAscending() {
        long[] ids = new long[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = store.addMessage(CHARACTER_ID, ChatMessage.Role.USER, "msg-" + i, 1000L + i);
        }

        List<ChatMessage> page = store.loadMessagesBefore(CHARACTER_ID, ids[3], 2);

        assertEquals("id < beforeId with limit 2 returns exactly the two rows before it",
                2, page.size());
        assertEquals("msg-1", page.get(0).getContent());
        assertEquals("msg-2", page.get(1).getContent());
        assertTrue("the boundary row itself must not be included",
                page.stream().noneMatch(m -> m.getId() == ids[3]));
    }

    @Test
    public void appendMessageVersion_thenSwitchBack_restoresOlderContent() {
        long messageId = store.addMessage(
                CHARACTER_ID, ChatMessage.Role.ASSISTANT, "version one", 1000L);

        store.appendMessageVersion(messageId, "version two", 1001L);

        ChatMessage afterRegenerate = store.loadMessage(messageId);
        assertEquals("regenerating updates the visible content", "version two",
                afterRegenerate.getContent());
        assertEquals(2, afterRegenerate.getVersionCount());
        assertEquals(2, afterRegenerate.getActiveVersion());

        ChatMessage switchedBack = store.switchMessageVersion(messageId, 1);
        assertEquals("version one", switchedBack.getContent());
        assertEquals(1, switchedBack.getActiveVersion());
        assertEquals("swiping back does not discard the newer version",
                2, switchedBack.getVersionCount());

        ChatMessage reloaded = store.loadMessage(messageId);
        assertEquals("the swipe is persisted, not just returned in-memory",
                "version one", reloaded.getContent());
    }

    @Test
    public void switchMessageVersion_clampsOutOfRangeTargetToValidBounds() {
        long messageId = store.addMessage(
                CHARACTER_ID, ChatMessage.Role.ASSISTANT, "only version", 1000L);
        store.appendMessageVersion(messageId, "second version", 1001L);

        ChatMessage clampedHigh = store.switchMessageVersion(messageId, 99);
        assertEquals("target beyond version_count clamps to the last version",
                "second version", clampedHigh.getContent());

        ChatMessage clampedLow = store.switchMessageVersion(messageId, -5);
        assertEquals("target below 1 clamps to the first version",
                "only version", clampedLow.getContent());
    }

    @Test
    public void clearMessages_deletesMessagesAndTheirVersionsForThatCharacterOnly() {
        long keptId = store.addMessage("other-character", ChatMessage.Role.USER, "keep me", 1000L);
        long removedId = store.addMessage(CHARACTER_ID, ChatMessage.Role.USER, "remove me", 1001L);
        store.appendMessageVersion(removedId, "remove me v2", 1002L);

        store.clearMessages(CHARACTER_ID);

        assertTrue("messages for the cleared character are gone",
                store.loadMessages(CHARACTER_ID).isEmpty());
        assertEquals("messages for other characters are untouched",
                1, store.loadMessages("other-character").size());
        assertEquals(keptId, store.loadMessages("other-character").get(0).getId());
    }

    @Test
    public void searchMessages_fallsBackToLikeWhenFtsTokenizationMissesASubstring() {
        // FTS4's default tokenizer splits on non-alphanumeric boundaries, so a
        // substring embedded inside a larger token (no word boundary) will not
        // FTS-match but must still be found by the LIKE fallback.
        store.addMessage(CHARACTER_ID, ChatMessage.Role.USER, "unmistakable", 1000L);

        List<ChatMessage> results = store.searchMessages(CHARACTER_ID, "mistak", 10);

        assertEquals(1, results.size());
        assertEquals("unmistakable", results.get(0).getContent());
    }

    @Test
    public void addGroupMessage_persistsSpeakerAttribution() {
        store.addGroupMessage(
                "group-1", ChatMessage.Role.ASSISTANT, "hello", 1000L,
                "member-1", "Alice");

        List<ChatMessage> messages = store.loadMessages("group-1");
        assertEquals(1, messages.size());
        assertEquals("Alice", messages.get(0).getSpeakerName());
    }
}
