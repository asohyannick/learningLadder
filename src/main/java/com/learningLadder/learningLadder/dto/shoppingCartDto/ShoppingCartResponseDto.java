package com.learningLadder.learningLadder.dto.shoppingCartDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.cartStatus.CartStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShoppingCartResponseDto(

        UUID id,
        UUID userId,
        CartStatus status,

        List<ShoppingCartItemResponseDto> items,

        String currency,
        int itemCount,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal total,

        String couponCode,
        BigDecimal couponDiscount,

        Instant lastActivityAt,
        Instant checkedOutAt,
        String checkoutReference,
        Instant abandonedAt,

        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public ShoppingCartResponseDto {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public long savedForLaterCount() {
        return items.stream().filter(ShoppingCartItemResponseDto::savedForLater).count();
    }

    public boolean empty() {
        return itemCount == 0;
    }

    public boolean editable() {
        return status != null && status.isEditable();
    }

    public boolean hasPriceChanges() {
        return items.stream().anyMatch(item -> item.priceChanged() && !item.savedForLater());
    }
}