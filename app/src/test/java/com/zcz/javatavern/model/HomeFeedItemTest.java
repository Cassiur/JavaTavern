package com.zcz.javatavern.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class HomeFeedItemTest {

    private static HomeFeedItem item(String id, long lastActivityAt) {
        return new HomeFeedItem(
                new CharacterProfile(id, "角色" + id, "描述", "开场白", 0),
                "预览" + id,
                lastActivityAt
        );
    }

    @Test
    public void byRecentSortsMostRecentFirst() {
        List<HomeFeedItem> items = new ArrayList<>(List.of(
                item("old", 100L),
                item("new", 300L),
                item("middle", 200L)
        ));
        items.sort(HomeFeedItem.BY_RECENT);
        assertEquals("new", items.get(0).getCharacter().getId());
        assertEquals("middle", items.get(1).getCharacter().getId());
        assertEquals("old", items.get(2).getCharacter().getId());
    }

    @Test
    public void byRecentPutsUnchattedCharactersLast() {
        List<HomeFeedItem> items = new ArrayList<>(List.of(
                item("no-chat", 0L),
                item("chatted", 50L)
        ));
        items.sort(HomeFeedItem.BY_RECENT);
        assertEquals("chatted", items.get(0).getCharacter().getId());
        assertEquals("no-chat", items.get(1).getCharacter().getId());
    }

    @Test
    public void exposesCharacterPreviewAndActivity() {
        CharacterProfile profile = new CharacterProfile("a", "名字", "描述", "开场白", 0);
        HomeFeedItem item = new HomeFeedItem(profile, "最后一条消息", 42L);
        assertEquals(profile, item.getCharacter());
        assertEquals("最后一条消息", item.getPreview());
        assertEquals(42L, item.getLastActivityAt());
    }
}
