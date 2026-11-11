package com.learningLadder.learningLadder.dto.paymentDto;

import java.io.Serializable;
import java.util.List;

public record PaymentPageResponseDto(
        List<PaymentSummaryResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public PaymentPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}