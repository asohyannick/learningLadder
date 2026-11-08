package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        @Schema(
                description = "Email address of the account",
                example = "student@asoh.io",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255
        )
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 256, message = "Email must not exceed 255 characters")
        String email,

        @Schema(
                description = "Account password",
                example = "********",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 128,
                format = "password"
        )
        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password must not exceed 128 characters")
        String password
) { }