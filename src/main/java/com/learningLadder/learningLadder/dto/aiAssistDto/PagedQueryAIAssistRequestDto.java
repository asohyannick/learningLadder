package com.learningLadder.learningLadder.dto.aiAssistDto;

import com.learningLadder.learningLadder.enums.aiProvider.AIProvider;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.UUID;

public record PagedQueryAIAssistRequestDto(

        @Size(max = 100, message = "Search keyword must not exceed 100 characters")
        String keyword,

        @Size(max = 50, message = "Purpose must not exceed 50 characters")
        String purpose,

        AIProvider provider,

        UUID courseId,

        Boolean active,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(regexp = "^(name|purpose|createdAt|updatedAt)$",
                message = "sortBy must be one of: name, purpose, createdAt, updatedAt")
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "name";
    public static final String DEFAULT_SORT_DIRECTION = "ASC";

    public PagedQueryAIAssistRequestDto {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        purpose = AIAssistValidation.toPurposeCode(purpose);
        active = active == null ? Boolean.TRUE : active;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }
}