package com.learningLadder.learningLadder.dto.paymentDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ConfirmManualPaymentRequestDto(

        @NotBlank(message = "The bank / mobile-money reference or cheque number is required")
        @Size(max = 100, message = "External reference must not exceed 100 characters")
        String externalReference,

        @Size(max = 50, message = "Receipt number must not exceed 50 characters")
        String receiptNumber,

        @PastOrPresent(message = "Payment date cannot be in the future")
        Instant paidAt,

        @Size(max = 1000, message = "Notes must not exceed 1,000 characters")
        String notes
) {

    public ConfirmManualPaymentRequestDto {
        externalReference = PaymentValidation.trim(externalReference);
        receiptNumber = PaymentValidation.trim(receiptNumber);
        notes = PaymentValidation.trim(notes);
    }
}