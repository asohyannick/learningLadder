package com.learningLadder.learningLadder.service.aiAssistantService.groq;

public record GroqCompletion(
        String id,
        String model,
        String content,
        String finishReason,
        GroqUsage usage,
        long latencyMs
) {

    public Integer promptTokens() {
        return usage == null ? null : usage.promptTokens();
    }

    public Integer completionTokens() {
        return usage == null ? null : usage.completionTokens();
    }

    public Integer totalTokens() {
        if (usage == null) {
            return null;
        }
        if (usage.totalTokens() != null) {
            return usage.totalTokens();
        }
        int prompt = usage.promptTokens() == null ? 0 : usage.promptTokens();
        int completion = usage.completionTokens() == null ? 0 : usage.completionTokens();
        return prompt + completion;
    }
}