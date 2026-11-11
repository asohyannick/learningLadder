package com.learningLadder.learningLadder.dto.aiAssistDto;

import java.io.Serializable;
import java.util.List;

public record AIAssistPageResponseDto(
        List<AIAssistResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public AIAssistPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}