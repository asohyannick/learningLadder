package com.learningLadder.learningLadder.dto.shoppingCartDto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShoppingCartItemResponseDto(
        UUID id,
        UUID cartId,
        UUID courseId,
        String courseTitle,
        String courseThumbnail,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        String currency,
        boolean savedForLater,
        boolean priceChanged,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public BigDecimal finalPrice() {
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        return price.subtract(discount).max(BigDecimal.ZERO);
    }

    public boolean free() {
        return finalPrice().signum() == 0;
    }
}