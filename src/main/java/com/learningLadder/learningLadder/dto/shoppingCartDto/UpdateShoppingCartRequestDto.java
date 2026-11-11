package com.learningLadder.learningLadder.dto.shoppingCartDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

/**
 * Cart-level changes (PATCH): apply or remove a coupon.
 * The discount amount is never sent by the client: the service validates the code
 * and calculates the discount itself.
 */
public record UpdateShoppingCartRequestDto(

        @Pattern(regexp = "^[A-Z0-9_-]{3,50}$",
                message = "Coupon code must be 3 to 50 letters, digits, '-' or '_'")
        String couponCode,

        Boolean removeCoupon
) {

    public UpdateShoppingCartRequestDto {
        couponCode = (couponCode == null || couponCode.isBlank())
                ? null : couponCode.trim().toUpperCase(Locale.ROOT);
        removeCoupon = removeCoupon != null && removeCoupon;
    }

    @JsonIgnore
    @AssertTrue(message = "Choose either couponCode or removeCoupon, not both")
    public boolean isCouponChoiceValid() {
        return !(removeCoupon && couponCode != null);
    }

    @JsonIgnore
    @AssertTrue(message = "Send a couponCode to apply, or removeCoupon = true")
    public boolean isNotEmpty() {
        return removeCoupon || couponCode != null;
    }
}