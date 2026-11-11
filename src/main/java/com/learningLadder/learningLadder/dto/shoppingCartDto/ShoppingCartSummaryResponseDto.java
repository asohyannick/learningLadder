package com.learningLadder.learningLadder.dto.shoppingCartDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.cartStatus.CartStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShoppingCartSummaryResponseDto(
        UUID id,
        UUID userId,
        CartStatus status,
        String currency,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total,
        String couponCode,
        Instant lastActivityAt,
        Instant checkedOutAt,
        String checkoutReference,
        Instant abandonedAt,
        Instant reminderSentAt,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {
}