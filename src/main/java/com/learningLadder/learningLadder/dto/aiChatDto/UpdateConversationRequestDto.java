package com.learningLadder.learningLadder.dto.aiChatDto;

import jakarta.validation.constraints.Size;

public record UpdateConversationRequestDto(

        @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
        String title,

        Boolean pinned,

        Boolean archived
) {

    public UpdateConversationRequestDto {
        title = title == null ? null : title.trim();
    }
}