package com.learningLadder.learningLadder.dto.couponDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.discountType.DiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record CreateCouponRequestDto(

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        @NotNull(message = "Discount type is required (PERCENTAGE or FIXED_AMOUNT)")
        DiscountType discountType,

        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.01", message = "Discount value must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Discount value must have at most 10 digits and 2 decimals")
        BigDecimal discountValue,

        @DecimalMin(value = "0.01", message = "Maximum discount must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Maximum discount must have at most 10 digits and 2 decimals")
        BigDecimal maxDiscountAmount,

        @DecimalMin(value = "0.00", message = "Minimum cart total cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Minimum cart total must have at most 10 digits and 2 decimals")
        BigDecimal minimumCartTotal,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g. USD, XAF)")
        String currency,

        UUID courseId,

        UUID assignedUserId,

        Instant startsAt,

        @NotNull(message = "An expiration date is required")
        @Future(message = "Expiration date must be in the future")
        Instant expiresAt,

        @Min(value = 1, message = "Maximum redemptions must be at least 1")
        @Max(value = 100_000, message = "Maximum redemptions must not exceed 100,000")
        Integer maxRedemptions
) {

    public static final Duration MAX_VALIDITY = Duration.ofDays(365);

    public CreateCouponRequestDto {
        description = description == null || description.isBlank() ? null : description.trim();
        currency = currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase(Locale.ROOT);
        if (maxRedemptions == null && assignedUserId != null) {
            maxRedemptions = 1;
        }
    }

    @JsonIgnore
    @AssertTrue(message = "A percentage discount cannot exceed 100")
    public boolean isPercentageValid() {
        return discountType != DiscountType.PERCENTAGE || discountValue == null
                || discountValue.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    @JsonIgnore
    @AssertTrue(message = "maxDiscountAmount only applies to PERCENTAGE coupons")
    public boolean isCapValid() {
        return maxDiscountAmount == null || discountType == DiscountType.PERCENTAGE;
    }

    @JsonIgnore
    @AssertTrue(message = "startsAt must be before expiresAt")
    public boolean isPeriodValid() {
        return startsAt == null || expiresAt == null || startsAt.isBefore(expiresAt);
    }

    @JsonIgnore
    @AssertTrue(message = "A coupon cannot be valid for more than 365 days")
    public boolean isValidityLengthValid() {
        if (expiresAt == null) {
            return true;
        }
        Instant from = startsAt == null ? Instant.now() : startsAt;
        return !expiresAt.isAfter(from.plus(MAX_VALIDITY));
    }
}