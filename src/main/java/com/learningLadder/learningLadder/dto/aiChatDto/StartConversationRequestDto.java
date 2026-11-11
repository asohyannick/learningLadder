package com.learningLadder.learningLadder.dto.aiChatDto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StartConversationRequestDto(

        @NotNull(message = "Assistant id is required")
        UUID assistantId,

        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Size(max = 32_000, message = "Message must not exceed 32,000 characters")
        String firstMessage
) {

    public StartConversationRequestDto {
        title = (title == null || title.isBlank()) ? null : title.trim();
        firstMessage = (firstMessage == null || firstMessage.isBlank()) ? null : firstMessage.trim();
    }
}