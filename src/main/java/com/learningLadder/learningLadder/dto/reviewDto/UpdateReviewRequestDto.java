package com.learningLadder.learningLadder.dto.reviewDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for updating a review. Fields left null are not changed.")
public record UpdateReviewRequestDto(

        @Schema(description = "Star rating from 1 to 5", example = "4")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must not exceed 5")
        Integer rating,

        @Schema(description = "Short headline for the review", example = "Good, but could go deeper")
        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,

        @Schema(description = "Full review text")
        @Size(max = 5000, message = "Comment must not exceed 5000 characters")
        String comment
) {
}