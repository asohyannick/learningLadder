package com.learningLadder.learningLadder.dto.bookingDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public record CreateBookingRequestDto(

        @NotNull(message = "Tutor id is required")
        UUID tutorId,

        UUID courseId,

        @NotBlank(message = "Topic is required")
        @Size(max = 200, message = "Topic must not exceed 200 characters")
        String topic,

        @Size(max = 5_000, message = "Notes must not exceed 5,000 characters")
        String studentNotes,

        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future")
        Instant scheduledStart,

        @NotNull(message = "End time is required")
        @Future(message = "End time must be in the future")
        Instant scheduledEnd,

        @Size(max = 50, message = "Time zone must not exceed 50 characters")
        String timeZone,

        @NotNull(message = "Session mode is required")
        SessionMode sessionMode,

        @Size(max = 50, message = "Meeting platform must not exceed 50 characters")
        String meetingPlatform,

        @Size(max = 200, message = "Location name must not exceed 200 characters")
        String locationName,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String addressLine,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "State/region must not exceed 100 characters")
        String stateRegion,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        @Digits(integer = 3, fraction = 6, message = "Latitude can have at most 6 decimal places")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        @Digits(integer = 3, fraction = 6, message = "Longitude can have at most 6 decimal places")
        BigDecimal longitude,

        @Size(max = 500, message = "Location instructions must not exceed 500 characters")
        String locationInstructions
) {

    public static final int MIN_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 8 * 60;

    public CreateBookingRequestDto {
        topic = trim(topic);
        studentNotes = trim(studentNotes);
        timeZone = (timeZone == null || timeZone.isBlank()) ? "UTC" : timeZone.trim();
        meetingPlatform = trim(meetingPlatform);
        locationName = trim(locationName);
        addressLine = trim(addressLine);
        city = trim(city);
        stateRegion = trim(stateRegion);
        country = trim(country);
        locationInstructions = trim(locationInstructions);
    }

    @JsonIgnore
    @AssertTrue(message = "End time must be after start time")
    public boolean isTimeRangeValid() {
        return scheduledStart == null || scheduledEnd == null || scheduledEnd.isAfter(scheduledStart);
    }

    @JsonIgnore
    @AssertTrue(message = "A session must last between 15 minutes and 8 hours")
    public boolean isDurationValid() {
        if (scheduledStart == null || scheduledEnd == null || !scheduledEnd.isAfter(scheduledStart)) {
            return true;
        }
        long minutes = Duration.between(scheduledStart, scheduledEnd).toMinutes();
        return minutes >= MIN_DURATION_MINUTES && minutes <= MAX_DURATION_MINUTES;
    }

    @JsonIgnore
    @AssertTrue(message = "Time zone must be a valid IANA zone, e.g. Africa/Douala")
    public boolean isTimeZoneValid() {
        return isValidZone(timeZone);
    }

    @JsonIgnore
    @AssertTrue(message = "Latitude and longitude must be provided together")
    public boolean isCoordinatesComplete() {
        return (latitude == null) == (longitude == null);
    }

    static boolean isValidZone(String zone) {
        if (zone == null) {
            return true;
        }
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException ex) {
            return false;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}