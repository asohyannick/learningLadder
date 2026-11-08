package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BlockAccountRequestDto(
        @Schema(
                description = "Why the account is being blocked",
                example = "Repeated violation of the academic integrity policy",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 5,
                maxLength = 500
        )
        @NotBlank(message = "Reason is required")
        @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
        String reason
) { }