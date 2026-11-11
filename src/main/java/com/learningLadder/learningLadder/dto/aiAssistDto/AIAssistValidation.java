package com.learningLadder.learningLadder.dto.aiAssistDto;

import java.text.Normalizer;
import java.util.Locale;

final class AIAssistValidation {

    static final String SLUG_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private AIAssistValidation() {
    }

    static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 100) {
            slug = slug.substring(0, 100).replaceAll("-$", "");
        }
        return slug.isEmpty() ? null : slug;
    }

    static String trim(String value) {
        return value == null ? null : value.trim();
    }

    static String toPurposeCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_|_$)", "");
    }
}