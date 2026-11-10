package com.learningLadder.learningLadder.dto.libraryLoanDto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ReportBookLostRequestDto(

        @NotBlank(message = "Please describe what happened")
        @Size(max = 1000, message = "Description must not exceed 1,000 characters")
        String description,

        @DecimalMin(value = "0.00", message = "Replacement fee cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Replacement fee must have at most 10 digits and 2 decimals")
        BigDecimal replacementFee
) {

    public ReportBookLostRequestDto {
        description = description == null ? null : description.trim();
    }
}