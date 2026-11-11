package com.learningLadder.learningLadder.dto.aiChatDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageFeedbackRequestDto(

        @NotNull(message = "Rating is required")
        Short rating,

        @Size(max = 500, message = "Comment must not exceed 500 characters")
        String comment
) {

    public MessageFeedbackRequestDto {
        comment = (comment == null || comment.isBlank()) ? null : comment.trim();
    }

    @JsonIgnore
    @AssertTrue(message = "Rating must be 1 (helpful) or -1 (not helpful)")
    public boolean isRatingValid() {
        return rating == null || rating == 1 || rating == -1;
    }
}