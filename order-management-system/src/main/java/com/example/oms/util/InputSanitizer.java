package com.example.oms.util;

/**
 * Defense-in-depth string sanitization. This project already prevents SQL
 * injection structurally by using PreparedStatements everywhere (see the
 * dao.impl package) — this class additionally trims/normalizes free-text
 * input and strips control characters before it ever reaches a query,
 * a log line, or a stored record.
 */
public final class InputSanitizer {

    private InputSanitizer() {
    }

    /** Trims whitespace and removes ASCII control characters. Returns null unchanged. */
    public static String clean(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Collapses runs of internal whitespace to a single space, after cleaning. */
    public static String normalizeWhitespace(String input) {
        String cleaned = clean(input);
        if (cleaned == null) {
            return null;
        }
        return cleaned.replaceAll("\\s+", " ");
    }
}
