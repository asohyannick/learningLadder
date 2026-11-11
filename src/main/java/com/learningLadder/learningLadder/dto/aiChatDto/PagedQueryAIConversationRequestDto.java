package com.learningLadder.learningLadder.dto.aiChatDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

public record PagedQueryAIConversationRequestDto(

        UUID assistantId,

        UUID userId,

        @Size(max = 100, message = "Search keyword must not exceed 100 characters")
        String keyword,

        Boolean pinned,

        Boolean archived,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant lastMessageFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant lastMessageTo,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(regexp = "^(lastMessageAt|createdAt|title|messageCount)$",
                message = "sortBy must be one of: lastMessageAt, createdAt, title, messageCount")
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "lastMessageAt";
    public static final String DEFAULT_SORT_DIRECTION = "DESC";

    public PagedQueryAIConversationRequestDto {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        archived = archived == null ? Boolean.FALSE : archived;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "lastMessageFrom must be before or equal to lastMessageTo")
    public boolean isDateRangeValid() {
        return lastMessageFrom == null || lastMessageTo == null || !lastMessageFrom.isAfter(lastMessageTo);
    }

    public Pageable toPageable() {
        Sort sort = Sort.by(Sort.Direction.DESC, "pinned")
                .and(Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
        return PageRequest.of(page, size, sort);
    }

    public PagedQueryAIConversationRequestDto withUserId(UUID forcedUserId) {
        return new PagedQueryAIConversationRequestDto(assistantId, forcedUserId, keyword, pinned, archived,
                lastMessageFrom, lastMessageTo, page, size, sortBy, sortDirection);
    }
}