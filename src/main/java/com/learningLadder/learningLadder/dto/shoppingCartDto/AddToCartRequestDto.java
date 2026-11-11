package com.learningLadder.learningLadder.dto.shoppingCartDto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;


public record AddToCartRequestDto(

        @NotNull(message = "Course id is required")
        UUID courseId,

        Boolean saveForLater
) {

    public AddToCartRequestDto {
        saveForLater = saveForLater != null && saveForLater;
    }
}