package com.zcz.javatavern.service;

import com.zcz.javatavern.model.CharacterProfile;

public interface ReplyEngine {
    String reply(CharacterProfile character, String userMessage);
}
