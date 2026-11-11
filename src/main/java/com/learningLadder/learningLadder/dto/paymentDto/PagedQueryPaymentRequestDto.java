package com.learningLadder.learningLadder.dto.paymentDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.paymentDirection.PaymentDirection;
import com.learningLadder.learningLadder.enums.paymentMethod.PaymentMethod;
import com.learningLadder.learningLadder.enums.paymentProvider.PaymentProvider;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.paymentType.PaymentType;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PagedQueryPaymentRequestDto(

        PaymentType paymentType,

        PaymentDirection direction,

        PaymentStatus status,

        PaymentProvider provider,

        PaymentMethod paymentMethod,

        UUID payerId,

        UUID payeeId,

        UUID studentId,

        UUID shoppingCartId,

        UUID involvedUserId,

        @Pattern(regexp = PaymentValidation.ACADEMIC_YEAR_PATTERN, message = "Academic year must look like 2026-2027")
        String academicYear,

        @Size(max = 30, message = "Term must not exceed 30 characters")
        String term,

        @Size(max = 50, message = "Fee category must not exceed 50 characters")
        String feeCategory,

        @Size(max = 100, message = "Search keyword must not exceed 100 characters")
        String keyword,

        @DecimalMin(value = "0.00", message = "minAmount cannot be negative")
        BigDecimal minAmount,

        @DecimalMin(value = "0.00", message = "maxAmount cannot be negative")
        BigDecimal maxAmount,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant paidFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant paidTo,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate payPeriodFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate payPeriodTo,

        Boolean overdueOnly,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(regexp = "^(createdAt|paidAt|amount|dueDate|payPeriodStart|status)$",
                message = "sortBy must be one of: createdAt, paidAt, amount, dueDate, payPeriodStart, status")
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "createdAt";
    public static final String DEFAULT_SORT_DIRECTION = "DESC";

    public PagedQueryPaymentRequestDto {
        academicYear = PaymentValidation.trim(academicYear);
        term = PaymentValidation.upper(term);
        feeCategory = PaymentValidation.upper(feeCategory);
        keyword = PaymentValidation.trim(keyword);
        overdueOnly = overdueOnly != null && overdueOnly;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = sortBy == null || sortBy.isBlank() ? DEFAULT_SORT_BY : sortBy;
        sortDirection = sortDirection == null || sortDirection.isBlank() ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "minAmount must be less than or equal to maxAmount")
    public boolean isAmountRangeValid() {
        return minAmount == null || maxAmount == null || minAmount.compareTo(maxAmount) <= 0;
    }

    @JsonIgnore
    @AssertTrue(message = "paidFrom must be before or equal to paidTo")
    public boolean isPaidRangeValid() {
        return paidFrom == null || paidTo == null || !paidFrom.isAfter(paidTo);
    }

    @JsonIgnore
    @AssertTrue(message = "payPeriodFrom must be before or equal to payPeriodTo")
    public boolean isPayPeriodRangeValid() {
        return payPeriodFrom == null || payPeriodTo == null || !payPeriodFrom.isAfter(payPeriodTo);
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }

    public PagedQueryPaymentRequestDto forUser(UUID userId) {
        return new PagedQueryPaymentRequestDto(paymentType, direction, status, provider, paymentMethod,
                null, null, studentId, shoppingCartId, userId, academicYear, term, feeCategory, keyword,
                minAmount, maxAmount, paidFrom, paidTo, payPeriodFrom, payPeriodTo, overdueOnly,
                page, size, sortBy, sortDirection);
    }
}