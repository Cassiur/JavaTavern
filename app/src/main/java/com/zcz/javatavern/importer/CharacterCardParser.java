package com.zcz.javatavern.importer;

import com.zcz.javatavern.model.CharacterCardData;
import com.zcz.javatavern.model.WorldBookEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public final class CharacterCardParser {
    public CharacterCardData parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            data = root;
        }

        String name = firstNonBlank(data.optString("name"), root.optString("name"));
        if (name.isEmpty()) {
            throw new JSONException("角色卡缺少 name 字段");
        }
        String description = firstNonBlank(
                data.optString("description"),
                data.optString("personality"),
                "导入的 AI 角色"
        );
        String greeting = firstNonBlank(
                data.optString("first_mes"),
                data.optString("first_message"),
                "你好。"
        );
        String systemPrompt = buildSystemPrompt(data, description);
        return new CharacterCardData(
                name,
                description,
                greeting,
                systemPrompt,
                sha256(json),
                parseWorldEntries(data, root)
        );
    }

    private String buildSystemPrompt(JSONObject data, String description) {
        List<String> sections = new ArrayList<>();
        addSection(sections, "角色描述", description);
        addSection(sections, "性格", data.optString("personality"));
        addSection(sections, "场景", data.optString("scenario"));
        addSection(sections, "角色规则", data.optString("system_prompt"));
        return String.join("\n\n", sections);
    }

    private List<WorldBookEntry> parseWorldEntries(JSONObject data, JSONObject root) {
        JSONObject characterBook = data.optJSONObject("character_book");
        if (characterBook == null) {
            characterBook = root.optJSONObject("character_book");
        }
        if (characterBook == null) {
            return List.of();
        }
        JSONArray rawEntries = characterBook.optJSONArray("entries");
        if (rawEntries == null) {
            return List.of();
        }

        List<WorldBookEntry> entries = new ArrayList<>();
        for (int index = 0; index < rawEntries.length(); index++) {
            JSONObject rawEntry = rawEntries.optJSONObject(index);
            if (rawEntry == null) {
                continue;
            }
            String content = rawEntry.optString("content").trim();
            if (content.isEmpty()) {
                continue;
            }
            entries.add(new WorldBookEntry(
                    parseKeywords(rawEntry.opt("keys")),
                    content,
                    rawEntry.optBoolean("enabled", true),
                    rawEntry.optBoolean("constant", false)
            ));
        }
        return entries;
    }

    private List<String> parseKeywords(Object rawKeys) {
        List<String> keywords = new ArrayList<>();
        if (rawKeys instanceof JSONArray array) {
            for (int index = 0; index < array.length(); index++) {
                addKeyword(keywords, array.optString(index));
            }
        } else if (rawKeys instanceof String stringKeys) {
            for (String keyword : stringKeys.split(",")) {
                addKeyword(keywords, keyword);
            }
        }
        return keywords;
    }

    private void addKeyword(List<String> keywords, String keyword) {
        String normalized = keyword.trim();
        if (!normalized.isEmpty()) {
            keywords.add(normalized);
        }
    }

    private void addSection(List<String> sections, String title, String value) {
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            sections.add(title + "：" + normalized);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = value == null ? "" : value.trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return "";
    }

    private String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("设备不支持 SHA-256", exception);
        }
    }
}
