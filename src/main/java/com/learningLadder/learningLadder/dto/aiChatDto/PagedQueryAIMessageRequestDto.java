package com.learningLadder.learningLadder.dto.aiChatDto;

import com.learningLadder.learningLadder.enums.aiMessageRole.AIMessageRole;
import com.learningLadder.learningLadder.enums.aiMessageStatus.AIMessageStatus;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PagedQueryAIMessageRequestDto(

        AIMessageRole role,

        AIMessageStatus status,

        Boolean flaggedOnly,

        Short feedbackRating,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(regexp = "^(sequenceNumber|createdAt)$", message = "sortBy must be sequenceNumber or createdAt")
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 30;
    public static final String DEFAULT_SORT_BY = "sequenceNumber";
    public static final String DEFAULT_SORT_DIRECTION = "DESC";

    public PagedQueryAIMessageRequestDto {
        flaggedOnly = flaggedOnly != null && flaggedOnly;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }
}