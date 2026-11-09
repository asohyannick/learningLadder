package com.learningLadder.learningLadder.dto.reviewDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Payload for creating a course review")
public record CreateReviewRequestDto(

        @Schema(description = "ID of the course being reviewed", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Course ID is required")
        UUID courseId,

        @Schema(description = "Star rating from 1 to 5", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must not exceed 5")
        Integer rating,

        @Schema(description = "Short headline for the review", example = "Excellent course")
        @Size(max = 150, message = "Title must not exceed 150 characters")
        String title,

        @Schema(description = "Full review text", example = "Clear explanations and great practical examples.")
        @Size(max = 5000, message = "Comment must not exceed 5000 characters")
        String comment
) {
}