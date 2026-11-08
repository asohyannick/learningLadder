package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpCodeDto(
        @Schema(
                description = "One-time code received by email",
                example = "482913",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 6,
                maxLength = 6
        )
        @NotBlank(message = "OTP code is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP code must be exactly 6 digits")
        String otpCode
) { }