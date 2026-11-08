package com.learningLadder.learningLadder.dto.timeTableDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import com.learningLadder.learningLadder.enums.timeTableStatus.TimeTableStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Schema(description = "Fields to change on a timetable entry. Only the fields you send are changed. "
        + "Send an empty string to clear description, notes, location or meetingUrl. "
        + "Setting recurring to false also clears dayOfWeek and endDate. "
        + "The rules between fields (times, recurrence, meeting link, location) are checked against the "
        + "entry as it will look after the update.")
public record UpdateTimeTableRequestDto(

        @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
        String title,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        String notes,

        LocalDate startDate,

        LocalDate endDate,

        @Schema(example = "09:00")
        LocalTime startTime,

        @Schema(example = "11:00")
        LocalTime endTime,

        @Schema(example = "Africa/Douala")
        @Size(max = 60, message = "Timezone must not exceed 60 characters")
        String timezone,

        Boolean recurring,

        DayOfWeek dayOfWeek,

        SessionMode sessionMode,

        @Size(max = 255, message = "Location must not exceed 255 characters")
        String location,

        @Size(max = 500, message = "Meeting URL must not exceed 500 characters")
        @Pattern(regexp = "^(https?://\\S+)?$", message = "Meeting URL must start with http:// or https://")
        String meetingUrl,

        @Min(value = 1, message = "Capacity must be at least 1")
        @Max(value = 100000, message = "Capacity must not exceed 100000")
        Integer capacity,

        @Schema(description = "Use CANCELLED or POSTPONED to flag a session without deleting it", example = "CANCELLED")
        TimeTableStatus status
) {

    /** Trims text. Empty text is kept on purpose: for the optional text fields it means "clear this". */
    public UpdateTimeTableRequestDto {
        title = trim(title);
        description = trim(description);
        notes = trim(notes);
        timezone = trim(timezone);
        location = trim(location);
        meetingUrl = trim(meetingUrl);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Provide at least one field to update")
    public boolean isAtLeastOneFieldProvided() {
        return title != null || description != null || notes != null
                || startDate != null || endDate != null || startTime != null || endTime != null
                || timezone != null || recurring != null || dayOfWeek != null || sessionMode != null
                || location != null || meetingUrl != null || capacity != null || status != null;
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