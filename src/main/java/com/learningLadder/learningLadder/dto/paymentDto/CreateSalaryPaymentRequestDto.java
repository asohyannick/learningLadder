package com.learningLadder.learningLadder.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.paymentMethod.PaymentMethod;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSalaryPaymentRequestDto(

        @NotNull(message = "Staff member (payeeUserId) is required")
        UUID payeeUserId,

        @NotNull(message = "Pay period start is required")
        LocalDate payPeriodStart,

        @NotNull(message = "Pay period end is required")
        LocalDate payPeriodEnd,

        @NotNull(message = "Gross amount is required")
        @DecimalMin(value = "0.01", message = "Gross amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Gross amount must have at most 10 digits and 2 decimals")
        BigDecimal grossAmount,

        @DecimalMin(value = "0.00", message = "Allowances cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Allowances must have at most 10 digits and 2 decimals")
        BigDecimal allowancesAmount,

        @DecimalMin(value = "0.00", message = "Deductions cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Deductions must have at most 10 digits and 2 decimals")
        BigDecimal deductionsAmount,

        @Pattern(regexp = PaymentValidation.CURRENCY_PATTERN, message = "Currency must be a 3-letter ISO code (e.g. USD, XAF)")
        String currency,

        PaymentMethod paymentMethod,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        @Size(max = 1000, message = "Notes must not exceed 1,000 characters")
        String notes
) {

    public CreateSalaryPaymentRequestDto {
        allowancesAmount = allowancesAmount == null ? BigDecimal.ZERO : allowancesAmount;
        deductionsAmount = deductionsAmount == null ? BigDecimal.ZERO : deductionsAmount;
        currency = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
        paymentMethod = paymentMethod == null ? PaymentMethod.BANK_TRANSFER : paymentMethod;
        description = PaymentValidation.trim(description);
        notes = PaymentValidation.trim(notes);
    }

    @JsonIgnore
    @AssertTrue(message = "Pay period end cannot be before its start")
    public boolean isPeriodValid() {
        return payPeriodStart == null || payPeriodEnd == null || !payPeriodEnd.isBefore(payPeriodStart);
    }

    @JsonIgnore
    @AssertTrue(message = "A pay period cannot be longer than 31 days")
    public boolean isPeriodLengthValid() {
        return payPeriodStart == null || payPeriodEnd == null || !payPeriodEnd.isAfter(payPeriodStart.plusDays(30));
    }

    @JsonIgnore
    @AssertTrue(message = "Deductions cannot be larger than gross amount plus allowances")
    public boolean isNetPositive() {
        return grossAmount == null || netAmount().signum() >= 0;
    }

    @JsonIgnore
    @AssertTrue(message = "Salaries are paid by BANK_TRANSFER, MOBILE_MONEY, CHEQUE or CASH")
    public boolean isPayoutMethodValid() {
        return paymentMethod == PaymentMethod.BANK_TRANSFER || paymentMethod == PaymentMethod.MOBILE_MONEY
                || paymentMethod == PaymentMethod.CHEQUE || paymentMethod == PaymentMethod.CASH;
    }

    @JsonIgnore
    public BigDecimal netAmount() {
        return grossAmount.add(allowancesAmount).subtract(deductionsAmount);
    }
}
