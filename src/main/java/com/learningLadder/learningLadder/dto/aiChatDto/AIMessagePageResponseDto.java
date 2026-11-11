package com.learningLadder.learningLadder.dto.aiChatDto;

import java.io.Serializable;
import java.util.List;

public record AIMessagePageResponseDto(
        List<AIMessageResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public AIMessagePageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}