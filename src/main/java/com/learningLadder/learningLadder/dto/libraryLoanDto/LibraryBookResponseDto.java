package com.learningLadder.learningLadder.dto.libraryLoanDto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read model of a catalog book. The course is exposed as its id; version is not exposed. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LibraryBookResponseDto(

        UUID id,

        String title,
        String subtitle,
        String author,
        String isbn,
        String publisher,
        Integer publicationYear,
        String edition,
        String language,
        String category,
        String description,
        Integer pageCount,
        String coverImage,
        UUID courseId,

        String shelfLocation,
        int totalCopies,
        int availableCopies,

        boolean borrowable,
        int loanPeriodDays,
        int maxRenewals,
        BigDecimal dailyLateFee,
        String currency,
        boolean active,

        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public int copiesOnLoan() {
        return Math.max(totalCopies - availableCopies, 0);
    }

    public boolean availableToBorrow() {
        return active && borrowable && availableCopies > 0;
    }
}