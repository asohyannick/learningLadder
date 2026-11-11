package com.learningLadder.learningLadder.dto.shoppingCartDto;

import java.io.Serializable;
import java.util.List;

public record ShoppingCartPageResponseDto(
        List<ShoppingCartSummaryResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public ShoppingCartPageResponseDto {
        content = content == null ? List.of() : List.copyOf(content);
    }
}