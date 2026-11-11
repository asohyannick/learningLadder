package com.learningLadder.learningLadder.dto.couponDto;

import java.io.Serializable;
import java.util.List;

public record CouponPageResponseDto(
        List<CouponResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public CouponPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}