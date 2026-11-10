package com.learningLadder.learningLadder.dto.libraryLoanDto;

import jakarta.validation.constraints.Size;

public record ReturnBookToLibraryRequestDto(

        @Size(max = 1000, message = "Note must not exceed 1,000 characters")
        String note
) {

    public ReturnBookToLibraryRequestDto {
        note = note == null || note.isBlank() ? null : note.trim();
    }
}