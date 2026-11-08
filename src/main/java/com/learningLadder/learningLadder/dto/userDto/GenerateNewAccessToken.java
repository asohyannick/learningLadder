package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenerateNewAccessToken(
        @Schema(
                description = "Refresh token issued at login, exchanged for a new access token",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Refresh token is required")
        @Size(max = 4096, message = "Refresh token is too long")
        String refreshToken
) { }