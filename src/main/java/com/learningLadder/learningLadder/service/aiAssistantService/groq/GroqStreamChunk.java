package com.learningLadder.learningLadder.service.aiAssistantService.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqStreamChunk(
        String id,
        String model,
        List<Choice> choices,
        GroqUsage usage,
        @JsonProperty("x_groq") XGroq xGroq
) {

    public GroqUsage resolvedUsage() {
        if (usage != null) {
            return usage;
        }
        return xGroq == null ? null : xGroq.usage();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(int index, Delta delta, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record XGroq(GroqUsage usage) {
    }
}