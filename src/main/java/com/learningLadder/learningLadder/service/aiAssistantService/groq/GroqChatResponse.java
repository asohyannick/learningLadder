package com.learningLadder.learningLadder.service.aiAssistantService.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqChatResponse(String id, String model, List<Choice> choices, GroqUsage usage) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(int index, GroqMessage message, @JsonProperty("finish_reason") String finishReason) {
    }
}