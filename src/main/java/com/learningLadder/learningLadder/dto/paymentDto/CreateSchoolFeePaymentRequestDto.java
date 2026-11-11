package com.learningLadder.learningLadder.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.paymentMethod.PaymentMethod;
import com.learningLadder.learningLadder.enums.paymentProvider.PaymentProvider;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSchoolFeePaymentRequestDto(

        @NotNull(message = "Student id is required")
        UUID studentId,

        @NotBlank(message = "Academic year is required, e.g. 2026-2027")
        @Pattern(regexp = PaymentValidation.ACADEMIC_YEAR_PATTERN, message = "Academic year must look like 2026-2027")
        String academicYear,

        @Size(max = 30, message = "Term must not exceed 30 characters")
        String term,

        @NotBlank(message = "Fee category is required, e.g. TUITION")
        @Size(max = 50, message = "Fee category must not exceed 50 characters")
        String feeCategory,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Amount must have at most 10 digits and 2 decimals")
        BigDecimal amount,

        @Pattern(regexp = PaymentValidation.CURRENCY_PATTERN, message = "Currency must be a 3-letter ISO code (e.g. USD, XAF)")
        String currency,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        LocalDate dueDate,

        @Min(value = 1, message = "Installment number must be at least 1")
        Integer installmentNumber,

        @Min(value = 1, message = "Total installments must be at least 1")
        @Max(value = 12, message = "Total installments must not exceed 12")
        Integer totalInstallments,

        PaymentProvider provider,

        PaymentMethod paymentMethod,

        @Size(max = 100, message = "External reference must not exceed 100 characters")
        String externalReference,

        @Size(max = 50, message = "Receipt number must not exceed 50 characters")
        String receiptNumber,

        @PastOrPresent(message = "Payment date cannot be in the future")
        Instant paidAt,

        @Size(max = 1000, message = "Notes must not exceed 1,000 characters")
        String notes
) {

    public CreateSchoolFeePaymentRequestDto {
        academicYear = PaymentValidation.trim(academicYear);
        term = PaymentValidation.upper(term);
        feeCategory = PaymentValidation.upper(feeCategory);
        currency = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase();
        description = PaymentValidation.trim(description);
        provider = provider == null ? PaymentProvider.STRIPE : provider;
        if (paymentMethod == null) {
            paymentMethod = provider == PaymentProvider.MANUAL ? PaymentMethod.CASH : PaymentMethod.CARD;
        }
        externalReference = PaymentValidation.trim(externalReference);
        receiptNumber = PaymentValidation.trim(receiptNumber);
        notes = PaymentValidation.trim(notes);
    }

    @JsonIgnore
    @AssertTrue(message = "Academic year must be two consecutive years, e.g. 2026-2027")
    public boolean isAcademicYearConsecutive() {
        return PaymentValidation.isConsecutiveAcademicYear(academicYear);
    }

    @JsonIgnore
    @AssertTrue(message = "Installment number cannot be greater than total installments")
    public boolean isInstallmentValid() {
        return installmentNumber == null || totalInstallments == null || installmentNumber <= totalInstallments;
    }

    @JsonIgnore
    @AssertTrue(message = "Manual payments need an externalReference or a receiptNumber")
    public boolean isManualPaymentTraceable() {
        return provider != PaymentProvider.MANUAL || externalReference != null || receiptNumber != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Manual payments use CASH, CHEQUE, BANK_TRANSFER or MOBILE_MONEY")
    public boolean isManualMethodValid() {
        return provider != PaymentProvider.MANUAL || paymentMethod == PaymentMethod.CASH
                || paymentMethod == PaymentMethod.CHEQUE || paymentMethod == PaymentMethod.BANK_TRANSFER
                || paymentMethod == PaymentMethod.MOBILE_MONEY;
    }

    @JsonIgnore
    @AssertTrue(message = "paidAt and receipt details are only for MANUAL payments")
    public boolean isOnlineWithoutManualFields() {
        return provider == PaymentProvider.MANUAL
                || (paidAt == null && externalReference == null && receiptNumber == null);
    }
}