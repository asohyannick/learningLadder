package com.learningLadder.learningLadder.dto.timeTableDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import com.learningLadder.learningLadder.enums.timeTableStatus.TimeTableStatus;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimeTablePagedQueryDto(

        @Parameter(description = "Matches the entry title and the course name", example = "algebra")
        @Size(max = 100, message = "Keyword must not exceed 100 characters")
        String keyword,

        @Parameter(description = "Only entries for this course")
        UUID courseId,

        @Parameter(description = "Only entries with this status", example = "SCHEDULED")
        TimeTableStatus status,

        @Parameter(description = "Only entries delivered this way", example = "ONLINE")
        SessionMode sessionMode,

        @Parameter(description = "Only recurring entries on this day of the week", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Parameter(description = "true for recurring entries only, false for one-off sessions only")
        Boolean recurring,

        @Parameter(description = "Only entries still running on or after this date (yyyy-MM-dd)", example = "2026-11-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @Parameter(description = "Only entries that start on or before this date (yyyy-MM-dd)", example = "2026-12-31")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        @Parameter(description = "Only entries starting at or after this time (HH:mm)", example = "08:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime startTimeFrom,

        @Parameter(description = "Only entries starting at or before this time (HH:mm)", example = "18:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        LocalTime startTimeTo,

        @Parameter(description = "Zero-based page number", example = "0")
        @Min(value = 0, message = "Page must be 0 or greater")
        Integer page,

        @Parameter(description = "Page size (1-100)", example = "20")
        @Min(value = 1, message = "Size must be at least 1")
        Integer size,

        @Parameter(description = "Field to sort by: title, startDate, startTime, endTime, status, sessionMode or createdAt",
                example = "startDate")
        @Pattern(regexp = "^(title|startDate|startTime|endTime|status|sessionMode|createdAt)$",
                message = "Sort field must be one of: title, startDate, startTime, endTime, status, sessionMode, createdAt")
        String sortBy,

        @Parameter(description = "Sort direction: asc or desc", example = "asc")
        @Pattern(regexp = "^(asc|desc)$", message = "Sort direction must be asc or desc")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT_BY = "startDate";
    public static final String DEFAULT_SORT_DIRECTION = "asc";

    public TimeTablePagedQueryDto {
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy.trim();
        sortDirection = (sortDirection == null || sortDirection.isBlank())
                ? DEFAULT_SORT_DIRECTION
                : sortDirection.trim().toLowerCase();
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "fromDate cannot be after toDate")
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null || !fromDate.isAfter(toDate);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "startTimeFrom cannot be after startTimeTo")
    public boolean isStartTimeRangeValid() {
        return startTimeFrom == null || startTimeTo == null || !startTimeFrom.isAfter(startTimeTo);
    }
}