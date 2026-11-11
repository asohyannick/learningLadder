package com.learningLadder.learningLadder.dto.aiAssistDto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AIAssistSummaryResponseDto(
        UUID id,
        String name,
        String slug,
        String description,
        String logo,
        String welcomeMessage,
        String purpose,
        UUID courseId,
        boolean streamingEnabled,
        int maxInputCharacters,
        int dailyMessageLimitPerUser
) implements Serializable {
}