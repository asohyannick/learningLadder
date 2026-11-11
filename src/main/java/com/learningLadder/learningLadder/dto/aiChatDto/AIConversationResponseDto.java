package com.learningLadder.learningLadder.dto.aiChatDto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AIConversationResponseDto(

        UUID id,

        UUID assistantId,
        String assistantName,
        String assistantLogo,

        UUID userId,

        String title,
        boolean pinned,
        boolean archived,

        int messageCount,
        long totalPromptTokens,
        long totalCompletionTokens,
        Instant lastMessageAt,


        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public long totalTokens() {
        return totalPromptTokens + totalCompletionTokens;
    }
}