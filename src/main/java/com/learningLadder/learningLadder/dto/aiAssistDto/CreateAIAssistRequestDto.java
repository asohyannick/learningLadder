package com.learningLadder.learningLadder.dto.aiAssistDto;

import com.learningLadder.learningLadder.enums.aiProvider.AIProvider;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAIAssistRequestDto(

        // ---------- Identity ----------
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        /** Optional: generated from the name when omitted, e.g. "Study Buddy" -> "study-buddy". */
        @Size(max = 100, message = "Slug must not exceed 100 characters")
        @Pattern(regexp = AIAssistValidation.SLUG_PATTERN,
                message = "Slug may only contain lowercase letters, digits and single hyphens")
        String slug,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Size(max = 1000, message = "Welcome message must not exceed 1,000 characters")
        String welcomeMessage,

        /** e.g. TUTORING, LIBRARY_HELP; normalized to UPPER_SNAKE_CASE. */
        @Size(max = 50, message = "Purpose must not exceed 50 characters")
        String purpose,

        UUID courseId,

        // ---------- Model configuration ----------
        AIProvider provider,

        @NotBlank(message = "Model is required (copy the model id from the provider's console)")
        @Size(max = 100, message = "Model must not exceed 100 characters")
        String model,

        @NotBlank(message = "System prompt is required")
        @Size(max = 20_000, message = "System prompt must not exceed 20,000 characters")
        String systemPrompt,

        @DecimalMin(value = "0.00", message = "Temperature must be between 0 and 2")
        @DecimalMax(value = "2.00", message = "Temperature must be between 0 and 2")
        @Digits(integer = 1, fraction = 2, message = "Temperature can have at most 2 decimal places")
        BigDecimal temperature,

        @DecimalMin(value = "0.00", inclusive = false, message = "Top P must be greater than 0 and at most 1")
        @DecimalMax(value = "1.00", message = "Top P must be greater than 0 and at most 1")
        @Digits(integer = 1, fraction = 2, message = "Top P can have at most 2 decimal places")
        BigDecimal topP,

        @Min(value = 1, message = "Max output tokens must be at least 1")
        @Max(value = 32_768, message = "Max output tokens must not exceed 32,768")
        Integer maxOutputTokens,

        @Min(value = 0, message = "Context window cannot be negative")
        @Max(value = 100, message = "Context window must not exceed 100 messages")
        Integer contextWindowMessages,

        Boolean streamingEnabled,

        // ---------- Limits & safety ----------
        @Min(value = 1, message = "Daily message limit must be at least 1")
        @Max(value = 10_000, message = "Daily message limit must not exceed 10,000")
        Integer dailyMessageLimitPerUser,

        @Min(value = 100, message = "Max input characters must be at least 100")
        @Max(value = 32_000, message = "Max input characters must not exceed 32,000")
        Integer maxInputCharacters,

        Boolean moderationEnabled
) {

    /** Normalizes input and applies the same defaults as the entity. */
    public CreateAIAssistRequestDto {
        name = AIAssistValidation.trim(name);
        slug = (slug == null || slug.isBlank()) ? AIAssistValidation.slugify(name) : slug.trim();
        description = AIAssistValidation.trim(description);
        welcomeMessage = AIAssistValidation.trim(welcomeMessage);
        purpose = AIAssistValidation.toPurposeCode(purpose);
        provider = provider == null ? AIProvider.GROQ : provider;
        model = AIAssistValidation.trim(model);
        systemPrompt = AIAssistValidation.trim(systemPrompt);
        temperature = temperature == null ? new BigDecimal("0.70") : temperature;
        topP = topP == null ? new BigDecimal("1.00") : topP;
        maxOutputTokens = maxOutputTokens == null ? 1024 : maxOutputTokens;
        contextWindowMessages = contextWindowMessages == null ? 20 : contextWindowMessages;
        streamingEnabled = streamingEnabled == null ? Boolean.TRUE : streamingEnabled;
        dailyMessageLimitPerUser = dailyMessageLimitPerUser == null ? 50 : dailyMessageLimitPerUser;
        maxInputCharacters = maxInputCharacters == null ? 4000 : maxInputCharacters;
        moderationEnabled = moderationEnabled == null ? Boolean.TRUE : moderationEnabled;
    }
}