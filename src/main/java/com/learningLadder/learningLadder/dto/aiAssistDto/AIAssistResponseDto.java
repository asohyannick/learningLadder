package com.learningLadder.learningLadder.dto.aiAssistDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.aiProvider.AIProvider;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AIAssistResponseDto(

        UUID id,

        // ---------- Identity ----------
        String name,
        String slug,
        String description,
        String logo,
        String welcomeMessage,
        String purpose,
        UUID courseId,

        // ---------- Model configuration ----------
        AIProvider provider,
        String model,
        String systemPrompt,
        BigDecimal temperature,
        BigDecimal topP,
        int maxOutputTokens,
        int contextWindowMessages,
        boolean streamingEnabled,

        // ---------- Limits & safety ----------
        int dailyMessageLimitPerUser,
        int maxInputCharacters,
        boolean moderationEnabled,
        boolean active,
        UUID createdBy,

        // ---------- Audit ----------
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}