package com.zcz.javatavern.network;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SseEventParser {
    public static final class Event {
        private final boolean done;
        private final String delta;

        Event(boolean done, String delta) {
            this.done = done;
            this.delta = delta;
        }

        public boolean isDone() {
            return done;
        }

        public String getDelta() {
            return delta;
        }
    }

    private SseEventParser() {
    }

    public static Event parse(String line) throws JSONException {
        if (line == null || !line.startsWith("data:")) {
            return new Event(false, "");
        }
        String data = line.substring(5).trim();
        if (data.equals("[DONE]")) {
            return new Event(true, "");
        }
        JSONObject root = new JSONObject(data);
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return new Event(false, "");
        }
        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
        return new Event(false, delta == null ? "" : delta.optString("content", ""));
    }
}
