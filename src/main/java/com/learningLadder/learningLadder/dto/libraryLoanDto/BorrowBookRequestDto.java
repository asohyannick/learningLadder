package com.learningLadder.learningLadder.dto.libraryLoanDto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record BorrowBookRequestDto(

        @NotNull(message = "Book id is required")
        UUID bookId,

        @Size(max = 1000, message = "Notes must not exceed 1,000 characters")
        String notes
) {

    public BorrowBookRequestDto {
        notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
}