package com.learningLadder.learningLadder.dto.userDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponseDto(
        @Schema(description = "Internal user ID", requiredMode = REQUIRED)
        UUID id,

        @Schema(description = "User's first name", requiredMode = NOT_REQUIRED, nullable = true)
        String firstName,

        @Schema(description = "User's last name", requiredMode = NOT_REQUIRED, nullable = true)
        String lastName,

        @Schema(description = "User's email address", requiredMode = REQUIRED)
        String email,

        @Schema(description = "Assigned role", requiredMode = REQUIRED)
        UserRole role,

        @Schema(description = "Whether the account is verified", requiredMode = REQUIRED)
        boolean isAccountVerified,

        @Schema(description = "Whether the account is soft-deleted", requiredMode = REQUIRED)
        boolean isAccountDeleted,

        @Schema(description = "Whether the account is blocked", requiredMode = REQUIRED)
        boolean isAccountBlocked,

        @Schema(description = "UTC time the account was created", example = "2026-09-16T13:05:35Z", requiredMode = REQUIRED)
        Instant createdAt,

        @Schema(description = "UTC time the account was last updated", example = "2026-09-16T13:05:35Z", requiredMode = REQUIRED)
        Instant updatedAt
) { }