package com.learningLadder.learningLadder.dto.shoppingCartDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.cartStatus.CartStatus;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record PagedQueryShoppingCartRequestDto(

        UUID userId,

        CartStatus status,

        UUID courseId,

        @Pattern(regexp = "^[A-Z0-9_-]{3,50}$",
                message = "Coupon code must be 3 to 50 letters, digits, '-' or '_'")
        String couponCode,

        @DecimalMin(value = "0.00", message = "minTotal cannot be negative")
        BigDecimal minTotal,

        @DecimalMin(value = "0.00", message = "maxTotal cannot be negative")
        BigDecimal maxTotal,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant lastActivityFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant lastActivityTo,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant checkedOutFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant checkedOutTo,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(regexp = "^(lastActivityAt|createdAt|updatedAt|checkedOutAt|total|itemCount)$",
                message = "sortBy must be one of: lastActivityAt, createdAt, updatedAt, checkedOutAt, total, itemCount")
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "lastActivityAt";
    public static final String DEFAULT_SORT_DIRECTION = "DESC";

    public PagedQueryShoppingCartRequestDto {
        couponCode = (couponCode == null || couponCode.isBlank()) ? null : couponCode.trim().toUpperCase(Locale.ROOT);
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "minTotal must be less than or equal to maxTotal")
    public boolean isTotalRangeValid() {
        return minTotal == null || maxTotal == null || minTotal.compareTo(maxTotal) <= 0;
    }

    @JsonIgnore
    @AssertTrue(message = "lastActivityFrom must be before or equal to lastActivityTo")
    public boolean isActivityRangeValid() {
        return lastActivityFrom == null || lastActivityTo == null || !lastActivityFrom.isAfter(lastActivityTo);
    }

    @JsonIgnore
    @AssertTrue(message = "checkedOutFrom must be before or equal to checkedOutTo")
    public boolean isCheckoutRangeValid() {
        return checkedOutFrom == null || checkedOutTo == null || !checkedOutFrom.isAfter(checkedOutTo);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }

    public PagedQueryShoppingCartRequestDto forUser(UUID forcedUserId) {
        return new PagedQueryShoppingCartRequestDto(forcedUserId, status, courseId, couponCode, minTotal, maxTotal,
                lastActivityFrom, lastActivityTo, checkedOutFrom, checkedOutTo, page, size, sortBy, sortDirection);
    }
}