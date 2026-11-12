package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TokenQueryDto(
        @Schema(description = "Reset token from the link in the email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Reset token is required")
        @Size(max = 255, message = "Reset token is too long")
        String token
) { }
