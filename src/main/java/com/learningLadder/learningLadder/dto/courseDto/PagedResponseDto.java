package com.learningLadder.learningLadder.dto.courseDto;

import org.springframework.data.domain.Page;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public record PagedResponseDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) implements Serializable {

    public static <T> PagedResponseDto<T> from(Page<T> page) {
        return new PagedResponseDto<>(
                new ArrayList<>(page.getContent()),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }
}