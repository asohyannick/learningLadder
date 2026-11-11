package com.learningLadder.learningLadder.service.aiAssistantService.groq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroqChatRequest(
        String model,
        List<GroqMessage> messages,
        BigDecimal temperature,
        @JsonProperty("top_p") BigDecimal topP,
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
        Boolean stream,
        String user
) {

    public GroqChatRequest withStream(boolean streaming) {
        return new GroqChatRequest(model, messages, temperature, topP, maxCompletionTokens, streaming, user);
    }
}