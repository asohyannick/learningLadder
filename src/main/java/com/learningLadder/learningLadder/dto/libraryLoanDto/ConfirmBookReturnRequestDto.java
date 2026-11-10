package com.learningLadder.learningLadder.dto.libraryLoanDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;


public record ConfirmBookReturnRequestDto(

        @NotBlank(message = "Condition on return is required")
        @Size(max = 255, message = "Condition must not exceed 255 characters")
        String conditionOnReturn,

        @PastOrPresent(message = "Return date cannot be in the future")
        Instant returnedAt,

        Boolean waiveLateFee,

        @DecimalMin(value = "0.00", message = "Late fee cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Late fee must have at most 10 digits and 2 decimals")
        BigDecimal lateFeeOverride,

        Boolean feePaid,

        @Size(max = 1000, message = "Notes must not exceed 1,000 characters")
        String notes
) {

    public ConfirmBookReturnRequestDto {
        conditionOnReturn = conditionOnReturn == null ? null : conditionOnReturn.trim();
        waiveLateFee = waiveLateFee != null && waiveLateFee;
        feePaid = feePaid != null && feePaid;
        notes = notes == null || notes.isBlank() ? null : notes.trim();
    }

    @JsonIgnore
    @AssertTrue(message = "Choose either waiveLateFee or lateFeeOverride, not both")
    public boolean isFeeChoiceValid() {
        return !(waiveLateFee && lateFeeOverride != null);
    }
}