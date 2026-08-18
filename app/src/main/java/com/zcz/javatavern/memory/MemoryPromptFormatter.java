package com.zcz.javatavern.memory;

import com.zcz.javatavern.model.MemoryEntry;

import java.util.List;

public final class MemoryPromptFormatter {
    private MemoryPromptFormatter() {
    }

    public static String format(List<MemoryEntry> entries, int characterLimit) {
        if (entries.isEmpty() || characterLimit <= 0) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        for (MemoryEntry entry : entries) {
            String line = "- " + entry.getContent().trim();
            if (line.length() <= 2) {
                continue;
            }
            int remaining = characterLimit - output.length();
            if (remaining <= 0) {
                break;
            }
            String separator = output.length() > 0 ? "\n" : "";
            if (separator.length() >= remaining) {
                break;
            }
            output.append(separator);
            remaining -= separator.length();
            if (line.length() > remaining) {
                output.append(line, 0, remaining);
                break;
            }
            output.append(line);
        }
        return output.toString();
    }
}
