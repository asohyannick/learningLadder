package com.learningLadder.learningLadder.dto.aiChatDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.aiMessageRole.AIMessageRole;
import com.learningLadder.learningLadder.enums.aiMessageStatus.AIMessageStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AIMessageResponseDto(

        UUID id,
        UUID conversationId,
        int sequenceNumber,
        AIMessageRole role,
        String content,
        AIMessageStatus status,

        String model,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Integer latencyMs,

        String errorCode,
        String errorMessage,

        boolean flagged,
        Short feedbackRating,
        String feedbackComment,

        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public boolean truncated() {
        return "length".equals(finishReason);
    }
}