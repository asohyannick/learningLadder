package com.learningLadder.learningLadder.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.paymentDirection.PaymentDirection;
import com.learningLadder.learningLadder.enums.paymentMethod.PaymentMethod;
import com.learningLadder.learningLadder.enums.paymentProvider.PaymentProvider;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.paymentType.PaymentType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponseDto(

        UUID id,
        String paymentReference,
        PaymentType paymentType,
        PaymentDirection direction,
        PaymentStatus status,
        String description,

        UUID payerId,
        UUID payeeId,
        UUID studentId,

        String currency,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal amount,
        BigDecimal providerFee,
        BigDecimal netAmount,
        BigDecimal refundedAmount,

        UUID shoppingCartId,
        String couponCode,
        BigDecimal couponDiscount,

        // ---------- School fee ----------
        String academicYear,
        String term,
        String feeCategory,
        LocalDate dueDate,
        Integer installmentNumber,
        Integer totalInstallments,

        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        BigDecimal grossAmount,
        BigDecimal allowancesAmount,
        BigDecimal deductionsAmount,
        UUID payslipDocumentId,
        UUID approvedBy,
        Instant approvedAt,

        UUID originalPaymentId,
        String refundReason,

        PaymentProvider provider,
        PaymentMethod paymentMethod,
        String providerPaymentId,
        String providerCheckoutSessionId,
        String providerStatus,
        String externalReference,
        String receiptUrl,
        String cardBrand,
        String cardLast4,
        String failureCode,
        String failureMessage,

        String receiptNumber,
        UUID receiptDocumentId,
        UUID recordedBy,
        String notes,

        Instant paidAt,
        Instant failedAt,
        Instant cancelledAt,
        Instant refundedAt,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public BigDecimal refundableAmount() {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        boolean refundable = status == PaymentStatus.SUCCEEDED || status == PaymentStatus.PARTIALLY_REFUNDED;
        BigDecimal refunded = refundedAmount == null ? BigDecimal.ZERO : refundedAmount;
        return refundable ? amount.subtract(refunded).max(BigDecimal.ZERO) : BigDecimal.ZERO;
    }

    public boolean overdue() {
        boolean unpaid = status == PaymentStatus.PENDING || status == PaymentStatus.REQUIRES_ACTION
                || status == PaymentStatus.PROCESSING || status == PaymentStatus.FAILED;
        return dueDate != null && unpaid && LocalDate.now().isAfter(dueDate);
    }
}