package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyMagicLinkTokenDto(
        @Schema(
                description = "Magic link token received by email",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255
        )
        @NotBlank(message = "Magic link token is required")
        @Size(max = 255, message = "Magic link token is too long")
        String magicLinkToken
) { }