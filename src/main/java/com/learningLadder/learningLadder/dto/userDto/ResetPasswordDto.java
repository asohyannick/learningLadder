package com.learningLadder.learningLadder.dto.userDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordDto(
        @Schema(
                description = "New password: 8-72 characters with upper and lower case letters, a digit and a special character",
                example = "********",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minLength = 8,
                maxLength = 72,
                format = "password"
        )
        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).+$",
                message = "Password must contain an uppercase letter, a lowercase letter, a digit and a special character"
        )
        String newPassword,

        @Schema(
                description = "Must be identical to newPassword",
                example = "********",
                requiredMode = Schema.RequiredMode.REQUIRED,
                format = "password"
        )
        @NotBlank(message = "Password confirmation is required")
        @Size(max = 72, message = "Password confirmation must not exceed 72 characters")
        String confirmPassword
) {

    @Schema(hidden = true)
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return newPassword == null || confirmPassword == null
                || newPassword.equals(confirmPassword);
    }
}