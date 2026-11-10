package com.learningLadder.learningLadder.dto.libraryLoanDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateLibraryBookRequestDto(

        @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
        String title,

        @Size(max = 255, message = "Subtitle must not exceed 255 characters")
        String subtitle,

        @Size(min = 1, max = 255, message = "Author must be between 1 and 255 characters")
        String author,

        @Pattern(regexp = "^(\\d{9}[\\dX]|\\d{13})$", message = "ISBN must be 10 or 13 digits (ISBN-10 may end with X)")
        String isbn,

        @Size(max = 150, message = "Publisher must not exceed 150 characters")
        String publisher,

        @Min(value = 1400, message = "Publication year must be 1400 or later")
        @Max(value = 2100, message = "Publication year must be 2100 or earlier")
        Integer publicationYear,

        @Size(max = 50, message = "Edition must not exceed 50 characters")
        String edition,

        @Size(max = 50, message = "Language must not exceed 50 characters")
        String language,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        @Size(max = 10_000, message = "Description must not exceed 10,000 characters")
        String description,

        @Min(value = 1, message = "Page count must be at least 1")
        @Max(value = 20_000, message = "Page count must not exceed 20,000")
        Integer pageCount,

        UUID courseId,

        @Size(max = 50, message = "Shelf location must not exceed 50 characters")
        String shelfLocation,

        @Min(value = 0, message = "Total copies cannot be negative")
        @Max(value = 10_000, message = "Total copies must not exceed 10,000")
        Integer totalCopies,

        Boolean borrowable,

        @Min(value = 1, message = "Loan period must be at least 1 day")
        @Max(value = 365, message = "Loan period must not exceed 365 days")
        Integer loanPeriodDays,

        @Min(value = 0, message = "Max renewals cannot be negative")
        @Max(value = 10, message = "Max renewals must not exceed 10")
        Integer maxRenewals,

        @DecimalMin(value = "0.00", message = "Daily late fee cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Daily late fee must have at most 8 digits and 2 decimals")
        BigDecimal dailyLateFee,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g. USD, XAF)")
        String currency,

        Boolean active
) {

    public UpdateLibraryBookRequestDto {
        title = trim(title);
        subtitle = trim(subtitle);
        author = trim(author);
        isbn = LibraryValidation.normalizeIsbn(isbn);
        publisher = trim(publisher);
        edition = trim(edition);
        language = trim(language);
        category = trim(category);
        shelfLocation = trim(shelfLocation);
        currency = currency == null ? null : currency.trim().toUpperCase();
    }

    @JsonIgnore
    @AssertTrue(message = "ISBN check digit is invalid")
    public boolean isIsbnChecksumValid() {
        return LibraryValidation.isValidIsbnChecksum(isbn);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}