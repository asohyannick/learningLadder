package com.learningLadder.learningLadder.dto.courseDto;

import com.learningLadder.learningLadder.enums.courseLevel.CourseLevel;
import com.learningLadder.learningLadder.enums.courseStatus.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Objects;

public record CoursePagedQueryDto(
        @Schema(description = "Keyword matched against name, short description and category")
        @Size(max = 100) String search,

        @Size(max = 100) String category,
        CourseLevel level,
        CourseStatus status,
        @Size(max = 50) String language,
        Boolean published,

        @DecimalMin("0.00") BigDecimal minPrice,
        @DecimalMin("0.00") BigDecimal maxPrice,

        @Schema(description = "Zero-based page index", example = "0")
        @Min(0) Integer page,

        @Schema(description = "Page size (max 100)", example = "20")
        @Min(1) @Max(100) Integer size,

        @Schema(description = "Sort field", example = "createdAt")
        @Pattern(regexp = "name|price|createdAt|updatedAt|publishedAt|durationMinutes",
                message = "sortBy must be one of: name, price, createdAt, updatedAt, publishedAt, durationMinutes")
        String sortBy,

        @Schema(description = "asc or desc", example = "desc")
        @Pattern(regexp = "(?i)asc|desc", message = "sortDirection must be asc or desc")
        String sortDirection
) {
    public CoursePagedQueryDto {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? "desc" : sortDirection.toLowerCase();
    }

    @Schema(hidden = true)
    @AssertTrue(message = "minPrice cannot be greater than maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    /** Stable cache key covering every parameter that changes the result. */
    public String cacheKey() {
        return String.join("|",
                Objects.toString(search == null ? null : search.trim().toLowerCase(), ""),
                Objects.toString(category == null ? null : category.trim().toLowerCase(), ""),
                Objects.toString(level, ""), Objects.toString(status, ""),
                Objects.toString(language == null ? null : language.trim().toLowerCase(), ""),
                Objects.toString(published, ""), Objects.toString(minPrice, ""), Objects.toString(maxPrice, ""),
                String.valueOf(page), String.valueOf(size), sortBy, sortDirection);
    }
}