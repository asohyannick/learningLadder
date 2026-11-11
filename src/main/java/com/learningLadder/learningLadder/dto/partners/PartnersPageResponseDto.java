package com.learningLadder.learningLadder.dto.partners;

import java.io.Serializable;
import java.util.List;

public record PartnersPageResponseDto(
        List<PartnersResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public PartnersPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}