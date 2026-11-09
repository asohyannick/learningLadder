package com.learningLadder.learningLadder.dto.bookingDto;

import java.io.Serializable;
import java.util.List;

public record BookingPageResponseDto(
        List<BookingResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public BookingPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}