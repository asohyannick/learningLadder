package com.learningLadder.learningLadder.dto.couponDto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RevokeCouponRequestDto(

        @NotBlank(message = "Please give a reason for revoking this coupon")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        String reason
) {

    public RevokeCouponRequestDto {
        reason = reason == null ? null : reason.trim();
    }
}