package com.learningLadder.learningLadder.dto.partners;

import java.io.Serializable;
import java.util.List;

public record PartnerPublicPageResponseDto(
        List<PartnerPublicResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public PartnerPublicPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}