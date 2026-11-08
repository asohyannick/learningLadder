package com.learningLadder.learningLadder.dto.timeTableDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

@Schema(description = "Details for a new timetable entry. The Idempotency-Key is sent as a header, not in this body.")
public record CreateTimeTableRequestDto(

        @Schema(description = "ID of the course this entry belongs to", example = "3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42")
        @NotNull(message = "Course is required")
        UUID courseId,

        @Schema(description = "Short title for the session", example = "Introduction to Algebra - Weekly Lecture")
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @Schema(description = "What the session covers")
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Schema(description = "Extra notes for learners, e.g. what to bring")
        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        String notes,

        @Schema(description = "Date of the session, or the first date when the entry is recurring", example = "2026-11-16")
        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @Schema(description = "Last date of a recurring entry. Leave empty for a one-off session", example = "2027-02-22")
        LocalDate endDate,

        @Schema(description = "Start time in the given timezone", example = "09:00")
        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @Schema(description = "End time in the given timezone. Must be after the start time", example = "11:00")
        @NotNull(message = "End time is required")
        LocalTime endTime,

        @Schema(description = "IANA timezone the times are expressed in. Defaults to UTC when omitted",
                example = "Africa/Douala")
        @Size(max = 60, message = "Timezone must not exceed 60 characters")
        String timezone,

        @Schema(description = "True when the session repeats weekly. Defaults to false", example = "true")
        boolean recurring,

        @Schema(description = "Day the session repeats on. Required when recurring, otherwise leave empty",
                example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(description = "How the session is delivered", example = "HYBRID")
        @NotNull(message = "Session mode is required")
        SessionMode sessionMode,

        @Schema(description = "Room or venue. Required for IN_PERSON and HYBRID sessions", example = "Block B, Room 204")
        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @Schema(description = "Meeting link. Required for ONLINE and HYBRID sessions",
                example = "https://meet.example.com/algebra-101")
        @Size(max = 500, message = "Meeting URL must not exceed 500 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "Meeting URL must start with http:// or https://")
        String meetingUrl,

        @Schema(description = "Maximum number of learners, if limited", example = "40")
        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 100000, message = "Capacity must not exceed 100000")
        Integer capacity
) {

    public CreateTimeTableRequestDto {
        title = clean(title);
        description = clean(description);
        notes = clean(notes);
        timezone = clean(timezone);
        location = clean(location);
        meetingUrl = clean(meetingUrl);
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "End time must be after start time")
    public boolean isEndTimeAfterStartTime() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "End date cannot be before start date")
    public boolean isEndDateNotBeforeStartDate() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Day of week and end date are required when the entry is recurring")
    public boolean isRecurrenceDetailsProvided() {
        return !recurring || (dayOfWeek != null && endDate != null);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Day of week and end date can only be set when the entry is recurring")
    public boolean isRecurrenceDetailsAllowed() {
        return recurring || (dayOfWeek == null && endDate == null);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Start date must fall on the selected day of week")
    public boolean isStartDateMatchesDayOfWeek() {
        return !recurring || dayOfWeek == null || startDate == null
                || startDate.getDayOfWeek() == dayOfWeek;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Meeting URL is required for online and hybrid sessions")
    public boolean isMeetingUrlProvidedWhenOnline() {
        return sessionMode == null || sessionMode == SessionMode.IN_PERSON || meetingUrl != null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Location is required for in-person and hybrid sessions")
    public boolean isLocationProvidedWhenInPerson() {
        return sessionMode == null || sessionMode == SessionMode.ONLINE || location != null;
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Timezone must be a valid IANA zone id, e.g. Africa/Douala")
    public boolean isTimezoneValid() {
        if (timezone == null) {
            return true;
        }
        try {
            ZoneId.of(timezone);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }
}