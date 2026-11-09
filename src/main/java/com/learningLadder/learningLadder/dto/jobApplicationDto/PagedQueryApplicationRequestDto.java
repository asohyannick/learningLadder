package com.learningLadder.learningLadder.dto.jobApplicationDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.applicationStatus.ApplicationStatus;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

public record PagedQueryApplicationRequestDto(

        UUID jobListingId,

        UUID applicantId,

        ApplicationStatus status,

        @Size(max = 100, message = "Search keyword must not exceed 100 characters")
        String keyword,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant submittedFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant submittedTo,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(
                regexp = "^(createdAt|submittedAt|updatedAt|status|lastName|yearsOfExperience)$",
                message = "sortBy must be one of: createdAt, submittedAt, updatedAt, status, lastName, yearsOfExperience"
        )
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "submittedAt";
    public static final String DEFAULT_SORT_DIRECTION = "DESC";

    public PagedQueryApplicationRequestDto {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "submittedFrom must be before or equal to submittedTo")
    public boolean isSubmittedRangeValid() {
        return submittedFrom == null || submittedTo == null || !submittedFrom.isAfter(submittedTo);
    }

    public Pageable toPageable() {
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }
}