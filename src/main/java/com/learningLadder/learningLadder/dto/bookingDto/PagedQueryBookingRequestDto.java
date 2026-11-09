package com.learningLadder.learningLadder.dto.bookingDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.bookingStatus.BookingStatus;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

public record PagedQueryBookingRequestDto(

        UUID tutorId,

        UUID studentId,

        UUID courseId,

        BookingStatus status,

        SessionMode sessionMode,

        @Size(max = 100, message = "Search keyword must not exceed 100 characters")
        String keyword,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant startFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant startTo,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(
                regexp = "^(scheduledStart|createdAt|updatedAt|status|durationMinutes|price)$",
                message = "sortBy must be one of: scheduledStart, createdAt, updatedAt, status, durationMinutes, price"
        )
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "scheduledStart";
    public static final String DEFAULT_SORT_DIRECTION = "ASC";

    public PagedQueryBookingRequestDto {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "startFrom must be before or equal to startTo")
    public boolean isStartRangeValid() {
        return startFrom == null || startTo == null || !startFrom.isAfter(startTo);
    }

    public Pageable toPageable() {
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}