package com.learningLadder.learningLadder.dto.paymentDto;

import java.util.Locale;

final class PaymentValidation {

    static final String CURRENCY_PATTERN = "^[A-Z]{3}$";
    static final String ACADEMIC_YEAR_PATTERN = "^\\d{4}-\\d{4}$";

    private PaymentValidation() {
    }

    static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    static boolean isConsecutiveAcademicYear(String academicYear) {
        if (academicYear == null || !academicYear.matches(ACADEMIC_YEAR_PATTERN)) {
            return true;
        }
        int start = Integer.parseInt(academicYear.substring(0, 4));
        int end = Integer.parseInt(academicYear.substring(5));
        return end == start + 1;
    }
}