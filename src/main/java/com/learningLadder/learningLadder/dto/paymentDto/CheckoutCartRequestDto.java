package com.learningLadder.learningLadder.dto.paymentDto;

import com.learningLadder.learningLadder.enums.paymentMethod.PaymentMethod;
import com.learningLadder.learningLadder.enums.paymentProvider.PaymentProvider;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

public record CheckoutCartRequestDto(

        PaymentProvider provider,

        PaymentMethod paymentMethod,

        Boolean acceptPriceChanges
) {

    public CheckoutCartRequestDto {
        provider = provider == null ? PaymentProvider.STRIPE : provider;
        paymentMethod = paymentMethod == null ? PaymentMethod.CARD : paymentMethod;
        acceptPriceChanges = acceptPriceChanges != null && acceptPriceChanges;
    }

    @JsonIgnore
    @AssertTrue(message = "Course purchases must be paid online (STRIPE or PAYPAL)")
    public boolean isOnlineProvider() {
        return provider != PaymentProvider.MANUAL;
    }

    @JsonIgnore
    @AssertTrue(message = "Online course purchases accept CARD, MOBILE_MONEY or PAYPAL_WALLET")
    public boolean isOnlineMethod() {
        return paymentMethod == PaymentMethod.CARD || paymentMethod == PaymentMethod.MOBILE_MONEY
                || paymentMethod == PaymentMethod.PAYPAL_WALLET;
    }
}