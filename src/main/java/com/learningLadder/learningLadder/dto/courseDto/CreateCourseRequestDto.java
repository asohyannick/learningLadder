package com.learningLadder.learningLadder.dto.courseDto;

import com.learningLadder.learningLadder.enums.courseLevel.CourseLevel;
import com.learningLadder.learningLadder.enums.courseStatus.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateCourseRequestDto(
        @Schema(description = "Course title", example = "Introduction to Algebra", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Course name is required")
        @Size(min = 3, max = 255, message = "Course name must be between 3 and 255 characters")
        String name,

        @Size(max = 500, message = "Short description must not exceed 500 characters")
        String shortDescription,

        @Size(max = 20000, message = "Description must not exceed 20000 characters")
        String description,

        @Size(max = 10000, message = "Learning outcomes must not exceed 10000 characters")
        String learningOutcomes,

        @Size(max = 10000, message = "Requirements must not exceed 10000 characters")
        String requirements,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        @Schema(description = "Defaults to ALL_LEVELS when omitted")
        CourseLevel level,

        @Size(max = 50, message = "Language must not exceed 50 characters")
        String language,

        @Min(value = 1, message = "Duration must be at least 1 minute")
        @Max(value = 525600, message = "Duration must not exceed one year")
        Integer durationMinutes,

        @Schema(description = "Defaults to 0", example = "49.99")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Price allows up to 10 digits and 2 decimals")
        BigDecimal price,

        @Schema(description = "Discount amount (same currency as price), defaults to 0", example = "5.00")
        @DecimalMin(value = "0.00", message = "Discount cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Discount allows up to 10 digits and 2 decimals")
        BigDecimal discount,

        @Schema(description = "ISO 4217 code, defaults to USD", example = "USD")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
        String currency,

        CourseStatus status,

        @Schema(description = "Publish immediately. Defaults to false")
        Boolean published
) {
    @Schema(hidden = true)
    @AssertTrue(message = "Discount cannot exceed price")
    public boolean isDiscountValid() {
        if (discount == null) return true;
        BigDecimal effectivePrice = price == null ? BigDecimal.ZERO : price;
        return discount.compareTo(effectivePrice) <= 0;
    }
}