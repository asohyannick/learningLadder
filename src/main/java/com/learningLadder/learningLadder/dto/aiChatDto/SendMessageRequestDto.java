package com.learningLadder.learningLadder.dto.aiChatDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequestDto(

        @NotBlank(message = "Message cannot be empty")
        @Size(max = 32_000, message = "Message must not exceed 32,000 characters")
        String content
) {

    public SendMessageRequestDto {
        content = content == null ? null : content.strip();
    }
}