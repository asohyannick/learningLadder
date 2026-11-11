package com.learningLadder.learningLadder.dto.couponDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.couponStatus.CouponStatus;
import com.learningLadder.learningLadder.enums.discountType.DiscountType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CouponResponseDto(
        UUID id,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        String currency,
        BigDecimal minimumCartTotal,
        UUID courseId,
        UUID assignedUserId,
        Instant startsAt,
        Instant expiresAt,
        Integer maxRedemptions,
        int redemptionCount,
        CouponStatus status,
        Instant revokedAt,
        UUID revokedBy,
        String revokeReason,
        UUID createdBy,
        String issuerRole,
        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public boolean expired() {
        return expiresAt != null && !Instant.now().isBefore(expiresAt);
    }

    public Integer remainingRedemptions() {
        return maxRedemptions == null ? null : Math.max(maxRedemptions - redemptionCount, 0);
    }

    public boolean usable() {
        Instant now = Instant.now();
        boolean started = startsAt == null || !now.isBefore(startsAt);
        boolean usesLeft = maxRedemptions == null || redemptionCount < maxRedemptions;
        return status == CouponStatus.ACTIVE && started && !expired() && usesLeft;
    }
}