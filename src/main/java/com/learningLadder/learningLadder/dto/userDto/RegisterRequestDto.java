package com.learningLadder.learningLadder.dto.userDto;

import com.learningLadder.learningLadder.enums.userRole.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequestDto(
        @Schema(
                description = "User's first name",
                example = "Amina",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255
        )
        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 255, message = "First name must be between 2 and 255 characters")
        @Pattern(
                regexp = "^[\\p{L}][\\p{L}\\p{M} '.-]*$",
                message = "First name may only contain letters, spaces, apostrophes, periods and hyphens"
        )
        String firstName,

        @Schema(
                description = "User's last name",
                example = "Nkemelu",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255
        )
        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 255, message = "Last name must be between 2 and 255 characters")
        @Pattern(
                regexp = "^[\\p{L}][\\p{L}\\p{M} '.-]*$",
                message = "Last name may only contain letters, spaces, apostrophes, periods and hyphens"
        )
        String lastName,

        @Schema(
                description = "Email address used to sign in",
                example = "student@asoh.io",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255
        )
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Schema(
                description = "Password: 8-72 characters with upper and lower case letters, a digit and a special character",
                example = "********",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 8,
                maxLength = 72,
                format = "password"
        )
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$",
                message = "Password must contain an uppercase letter, a lowercase letter, a digit and a special character"
        )
        String password,

        @Schema(
                description = "Role requested at registration",
                example = "STUDENT",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "Role is required")
        UserRole role
) { }
