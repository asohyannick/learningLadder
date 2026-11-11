package com.learningLadder.learningLadder.dto.paymentDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPaymentRequestDto(

        @NotBlank(message = "Please give a reason for cancelling")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {

    public CancelPaymentRequestDto {
        reason = reason == null ? null : reason.trim();
    }
}