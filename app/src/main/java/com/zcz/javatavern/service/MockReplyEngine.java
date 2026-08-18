package com.zcz.javatavern.service;

import com.zcz.javatavern.model.CharacterProfile;

public final class MockReplyEngine implements ReplyEngine {
    @Override
    public String reply(CharacterProfile character, String userMessage) {
        String normalized = userMessage.trim();
        if (normalized.endsWith("？") || normalized.endsWith("?")) {
            return "你问的是：“" + normalized + "”\n\n先给我一个你自己的判断，我会沿着它继续追问。";
        }
        if (normalized.length() < 8) {
            return "我听见了。能再补充一个具体事实吗？";
        }
        return "我先记下这句话。现在把它缩成一个可以在今天完成的小动作，你会选什么？";
    }
}
