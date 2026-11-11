package com.learningLadder.learningLadder.dto.couponDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.couponStatus.CouponStatus;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

public record PagedQueryCouponRequestDto(

        CouponStatus status,

        UUID courseId,

        UUID assignedUserId,

        UUID createdBy,

        Boolean usableOnly,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant expiresFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant expiresTo,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(regexp = "^(createdAt|expiresAt|redemptionCount|discountValue)$",
                message = "sortBy must be one of: createdAt, expiresAt, redemptionCount, discountValue")
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public PagedQueryCouponRequestDto {
        usableOnly = usableOnly != null && usableOnly;
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sortBy = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy;
        sortDirection = sortDirection == null || sortDirection.isBlank() ? "DESC" : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "expiresFrom must be before or equal to expiresTo")
    public boolean isExpiryRangeValid() {
        return expiresFrom == null || expiresTo == null || !expiresFrom.isAfter(expiresTo);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }

    public PagedQueryCouponRequestDto forIssuer(UUID issuerId) {
        return new PagedQueryCouponRequestDto(status, courseId, assignedUserId, issuerId, usableOnly,
                expiresFrom, expiresTo, page, size, sortBy, sortDirection);
    }

    public PagedQueryCouponRequestDto forStudent(UUID studentId) {
        return new PagedQueryCouponRequestDto(status, courseId, studentId, null, usableOnly,
                expiresFrom, expiresTo, page, size, sortBy, sortDirection);
    }
}