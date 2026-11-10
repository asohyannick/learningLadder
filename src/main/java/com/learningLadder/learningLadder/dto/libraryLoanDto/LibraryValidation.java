package com.learningLadder.learningLadder.dto.libraryLoanDto;

final class LibraryValidation {

    private LibraryValidation() {
    }

    static String normalizeIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        return isbn.replaceAll("[\\s-]", "").toUpperCase();
    }

    static boolean isValidIsbnChecksum(String isbn) {
        if (isbn == null) {
            return true;
        }
        if (isbn.matches("\\d{9}[\\dX]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) {
                char c = isbn.charAt(i);
                int value = (c == 'X') ? 10 : c - '0';
                sum += value * (10 - i);
            }
            return sum % 11 == 0;
        }
        if (isbn.matches("\\d{13}")) {
            int sum = 0;
            for (int i = 0; i < 13; i++) {
                int digit = isbn.charAt(i) - '0';
                sum += (i % 2 == 0) ? digit : digit * 3;
            }
            return sum % 10 == 0;
        }
        return true;
    }
}