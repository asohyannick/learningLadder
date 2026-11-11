package com.learningLadder.learningLadder.dto.aiChatDto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatStreamEventDto(
        String type,
        UUID conversationId,
        UUID messageId,
        String delta,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer remainingMessagesToday,
        String errorCode,
        String errorMessage
) {

    public static ChatStreamEventDto start(UUID conversationId, UUID messageId) {
        return new ChatStreamEventDto("start", conversationId, messageId, null, null, null, null, null, null, null);
    }

    public static ChatStreamEventDto delta(UUID conversationId, UUID messageId, String text) {
        return new ChatStreamEventDto("delta", conversationId, messageId, text, null, null, null, null, null, null);
    }

    public static ChatStreamEventDto done(UUID conversationId, UUID messageId, String finishReason,
                                          Integer promptTokens, Integer completionTokens, int remainingMessagesToday) {
        return new ChatStreamEventDto("done", conversationId, messageId, null, finishReason,
                promptTokens, completionTokens, remainingMessagesToday, null, null);
    }

    public static ChatStreamEventDto error(UUID conversationId, UUID messageId, String errorCode, String errorMessage) {
        return new ChatStreamEventDto("error", conversationId, messageId, null, null, null, null, null, errorCode, errorMessage);
    }
}