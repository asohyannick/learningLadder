package com.learningLadder.learningLadder.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.paymentProvider.PaymentProvider;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckoutResponseDto(
        UUID paymentId,
        String paymentReference,
        PaymentStatus status,
        PaymentProvider provider,
        BigDecimal amount,
        String currency,
        String clientSecret,
        String publishableKey,
        String redirectUrl,
        Instant expiresAt,
        boolean freeOfCharge
) {
}