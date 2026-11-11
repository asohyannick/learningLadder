package com.learningLadder.learningLadder.dto.aiAssistDto;

import com.learningLadder.learningLadder.enums.aiProvider.AIProvider;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateAIAssistRequestDto(

        @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
        String name,

        @Size(max = 100, message = "Slug must not exceed 100 characters")
        @Pattern(regexp = AIAssistValidation.SLUG_PATTERN,
                message = "Slug may only contain lowercase letters, digits and single hyphens")
        String slug,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        @Size(max = 1000, message = "Welcome message must not exceed 1,000 characters")
        String welcomeMessage,

        @Size(max = 50, message = "Purpose must not exceed 50 characters")
        String purpose,

        UUID courseId,

        AIProvider provider,

        @Size(min = 1, max = 100, message = "Model must be between 1 and 100 characters")
        String model,

        @Size(min = 1, max = 20_000, message = "System prompt must be between 1 and 20,000 characters")
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

        @Min(value = 1, message = "Daily message limit must be at least 1")
        @Max(value = 10_000, message = "Daily message limit must not exceed 10,000")
        Integer dailyMessageLimitPerUser,

        @Min(value = 100, message = "Max input characters must be at least 100")
        @Max(value = 32_000, message = "Max input characters must not exceed 32,000")
        Integer maxInputCharacters,

        Boolean moderationEnabled,

        Boolean active
) {

    public UpdateAIAssistRequestDto {
        name = AIAssistValidation.trim(name);
        slug = AIAssistValidation.trim(slug);
        description = AIAssistValidation.trim(description);
        welcomeMessage = AIAssistValidation.trim(welcomeMessage);
        purpose = AIAssistValidation.toPurposeCode(purpose);
        model = AIAssistValidation.trim(model);
        systemPrompt = AIAssistValidation.trim(systemPrompt);
    }
}