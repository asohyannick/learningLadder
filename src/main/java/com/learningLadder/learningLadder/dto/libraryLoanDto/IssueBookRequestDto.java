package com.learningLadder.learningLadder.dto.libraryLoanDto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record IssueBookRequestDto(

        @Size(max = 255, message = "Condition must not exceed 255 characters")
        String conditionOnBorrow,

        @Future(message = "Due date must be in the future")
        Instant dueAt,

        @Size(max = 1000, message = "Notes must not exceed 1,000 characters")
        String notes
) {

    public IssueBookRequestDto {
        conditionOnBorrow = conditionOnBorrow == null ? null : conditionOnBorrow.trim();
        notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
}