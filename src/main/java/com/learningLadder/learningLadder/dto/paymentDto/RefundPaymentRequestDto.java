package com.learningLadder.learningLadder.dto.paymentDto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundPaymentRequestDto(

        @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Refund amount must have at most 10 digits and 2 decimals")
        BigDecimal amount,

        @NotBlank(message = "Please give a reason for the refund")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {

    public RefundPaymentRequestDto {
        reason = reason == null ? null : reason.trim();
    }
}