package com.learningLadder.learningLadder.dto.reviewDto;

import com.learningLadder.learningLadder.enums.reviewStatus.ReviewStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Filters, sorting and paging for searching reviews")
public record ReviewPagedQueryDto(

        @Schema(description = "Matches the review title, comment, course name or reviewer name")
        String keyword,

        @Schema(description = "Only reviews of this course")
        UUID courseId,

        @Schema(description = "Only reviews written by this student")
        UUID studentId,

        @Schema(description = "Exact star rating")
        @Min(1) @Max(5)
        Integer rating,

        @Schema(description = "Minimum star rating (inclusive)")
        @Min(1) @Max(5)
        Integer minRating,

        @Schema(description = "Maximum star rating (inclusive)")
        @Min(1) @Max(5)
        Integer maxRating,

        @Schema(description = "Moderation status")
        ReviewStatus status,

        @Schema(description = "Only reviews from verified enrollments")
        Boolean verifiedEnrollment,

        @Schema(description = "true = only reviews with a tutor reply, false = only without")
        Boolean hasReply,

        @Schema(description = "Created on or after this date", example = "2026-01-01")
        LocalDate fromDate,

        @Schema(description = "Created on or before this date", example = "2026-12-31")
        LocalDate toDate,

        @Schema(description = "Zero-based page index", example = "0", defaultValue = "0")
        @Min(0)
        Integer page,

        @Schema(description = "Page size (1-100)", example = "20", defaultValue = "20")
        @Min(1) @Max(100)
        Integer size,

        @Schema(description = "Sort field: createdAt, updatedAt, rating or helpfulCount",
                example = "createdAt", defaultValue = "createdAt")
        String sortBy,

        @Schema(description = "asc or desc", example = "desc", defaultValue = "desc")
        String sortDirection
) {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "updatedAt", "rating", "helpfulCount");

    public ReviewPagedQueryDto {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        page = (page == null) ? 0 : page;
        size = (size == null) ? 20 : size;
        sortBy = (sortBy != null && SORTABLE_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        sortDirection = "asc".equalsIgnoreCase(sortDirection) ? "asc" : "desc";
    }

    @Schema(hidden = true)
    @AssertTrue(message = "minRating must not be greater than maxRating")
    public boolean isRatingRangeValid() {
        return minRating == null || maxRating == null || minRating <= maxRating;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "fromDate must not be after toDate")
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }
}