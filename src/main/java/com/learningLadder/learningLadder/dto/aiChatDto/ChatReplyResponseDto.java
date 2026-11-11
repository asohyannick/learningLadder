package com.learningLadder.learningLadder.dto.aiChatDto;

import java.io.Serializable;
import java.util.UUID;

public record ChatReplyResponseDto(
        UUID conversationId,
        String conversationTitle,
        AIMessageResponseDto userMessage,
        AIMessageResponseDto assistantMessage,
        int remainingMessagesToday
) implements Serializable {
}