package com.learningLadder.learningLadder.service.aiAssistantService.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqMessage(String role, String content) {

    public static GroqMessage system(String content) {
        return new GroqMessage("system", content);
    }

    public static GroqMessage user(String content) {
        return new GroqMessage("user", content);
    }

    public static GroqMessage assistant(String content) {
        return new GroqMessage("assistant", content);
    }
}