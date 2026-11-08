package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirebaseIdTokenDto(
        @Schema(
                description = "Firebase ID token (JWT) obtained from the client SDK after sign-in",
                example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "ID token is required")
        @Size(max = 4096, message = "ID token is too long")
        String idToken
) { }