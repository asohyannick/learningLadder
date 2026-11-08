package com.learningLadder.learningLadder.dto.userDto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequestDto(
        @NotBlank(message = "Refresh token is required to log out.")
        String refreshToken
) { }