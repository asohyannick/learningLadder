package com.learningLadder.learningLadder.dto.shoppingCartDto;

import jakarta.validation.constraints.NotNull;

public record UpdateCartItemRequestDto(

        @NotNull(message = "savedForLater is required")
        Boolean savedForLater
) {
}