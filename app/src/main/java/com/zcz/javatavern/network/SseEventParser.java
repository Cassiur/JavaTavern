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

    private static final Event EMPTY = new Event(false, "");

    private SseEventParser() {
    }

    /**
     * Never throws: a line that is not valid JSON (a truncated chunk, a
     * non-conforming proxy's keep-alive/comment frame, ...) is treated as
     * noise and skipped, so one bad line does not abort an otherwise
     * in-progress reply.
     */
    public static Event parse(String line) {
        if (line == null || !line.startsWith("data:")) {
            return EMPTY;
        }
        String data = line.substring(5).trim();
        if (data.equals("[DONE]")) {
            return new Event(true, "");
        }
        try {
            JSONObject root = new JSONObject(data);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return EMPTY;
            }
            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
            return new Event(false, delta == null ? "" : delta.optString("content", ""));
        } catch (JSONException malformed) {
            return EMPTY;
        }
    }
}
