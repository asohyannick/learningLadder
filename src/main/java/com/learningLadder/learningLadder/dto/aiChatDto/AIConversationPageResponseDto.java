package com.learningLadder.learningLadder.dto.aiChatDto;

import java.io.Serializable;
import java.util.List;

public record AIConversationPageResponseDto(
        List<AIConversationResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public AIConversationPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}