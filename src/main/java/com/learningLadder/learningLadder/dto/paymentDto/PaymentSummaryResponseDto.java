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
public record PaymentSummaryResponseDto(
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
        BigDecimal amount,
        BigDecimal refundedAmount,
        PaymentProvider provider,
        PaymentMethod paymentMethod,
        String academicYear,
        String term,
        String feeCategory,
        LocalDate dueDate,
        LocalDate payPeriodStart,
        LocalDate payPeriodEnd,
        String receiptNumber,
        Instant paidAt,
        Instant createdAt
) implements Serializable {
}