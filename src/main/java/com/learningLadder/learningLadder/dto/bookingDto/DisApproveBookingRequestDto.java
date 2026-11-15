package com.learningLadder.learningLadder.dto.bookingDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisApproveBookingRequestDto(

        @Schema(description = "Why the booking is declined (shown to the student)",
                example = "I am travelling that week. Please book another time.")
        @NotBlank(message = "Please give the student a reason for declining")
        @Size(min = 5, max = 500, message = "Reason must be between 5 and 500 characters")
        String reason
) {
    public DisApproveBookingRequestDto {
        reason = reason == null ? null : reason.trim();
    }
}