package com.learningLadder.learningLadder.dto.libraryLoanDto;

import java.io.Serializable;
import java.util.List;

public record LibraryLoanPageResponseDto(
        List<LibraryLoanResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public LibraryLoanPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}