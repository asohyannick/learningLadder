package com.learningLadder.learningLadder.dto.libraryLoanDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.UUID;

public record PagedQueryLibraryBookRequestDto(

        @Size(max = 100, message = "Search keyword must not exceed 100 characters")
        String keyword,

        @Size(max = 100, message = "Category must not exceed 100 characters")
        String category,

        @Size(max = 50, message = "Language must not exceed 50 characters")
        String language,

        UUID courseId,

        Boolean availableOnly,

        Boolean active,

        @Min(value = 1400, message = "publicationYearFrom must be 1400 or later")
        Integer publicationYearFrom,

        @Max(value = 2100, message = "publicationYearTo must be 2100 or earlier")
        Integer publicationYearTo,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(
                regexp = "^(title|author|publicationYear|createdAt|availableCopies|category)$",
                message = "sortBy must be one of: title, author, publicationYear, createdAt, availableCopies, category"
        )
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "title";
    public static final String DEFAULT_SORT_DIRECTION = "ASC";

    public PagedQueryLibraryBookRequestDto {
        keyword = blankToNull(keyword);
        category = blankToNull(category);
        language = blankToNull(language);
        availableOnly = availableOnly != null && availableOnly;
        active = active == null ? Boolean.TRUE : active;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = blankToNull(sortBy) == null ? DEFAULT_SORT_BY : sortBy;
        sortDirection = blankToNull(sortDirection) == null ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "publicationYearFrom must be before or equal to publicationYearTo")
    public boolean isYearRangeValid() {
        return publicationYearFrom == null || publicationYearTo == null || publicationYearFrom <= publicationYearTo;
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(sortDirection), sortBy));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}