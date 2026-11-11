package com.learningLadder.learningLadder.dto.partners;

import java.text.Normalizer;
import java.util.Locale;

final class PartnerValidation {

    static final String SLUG_PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";
    static final String PHONE_PATTERN = "^\\+?[0-9 ()-]{7,30}$";
    static final String CURRENCY_PATTERN = "^[A-Z]{3}$";

    private PartnerValidation() {
    }

    static String trim(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.length() > 150) {
            slug = slug.substring(0, 150).replaceAll("-$", "");
        }
        return slug.isEmpty() ? null : slug;
    }

    static String normalizeUrl(String value) {
        String url = trim(value);
        if (url == null) {
            return null;
        }
        return url.matches("(?i)^https?://.*") ? url : "https://" + url;
    }

    static String normalizeEmail(String value) {
        String email = trim(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }

    static String normalizeCurrency(String value) {
        String currency = trim(value);
        return currency == null ? null : currency.toUpperCase(Locale.ROOT);
    }
}