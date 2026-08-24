package com.zcz.javatavern.model;

import java.util.List;

/**
 * 群聊：多个角色同场对话的房间。
 *
 * <p>成员是已存在的角色（{@link CharacterProfile}）的 id 列表。群聊消息仍复用
 * {@code messages} 表（character_id 指向群 id），发言角色由消息的 speaker 字段标记。
 */
public final class Group {
    private final String id;
    private final String name;
    private final List<String> memberIds;
    private final long createdAt;

    public Group(String id, String name, List<String> memberIds, long createdAt) {
        this.id = id;
        this.name = name;
        this.memberIds = List.copyOf(memberIds);
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
