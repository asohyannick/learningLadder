package com.learningLadder.learningLadder.dto.bookingDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record UpdateBookingRequestDto(

        UUID courseId,

        @Size(min = 1, max = 200, message = "Topic must be between 1 and 200 characters")
        String topic,

        @Size(max = 5_000, message = "Notes must not exceed 5,000 characters")
        String studentNotes,

        @Future(message = "Start time must be in the future")
        Instant scheduledStart,

        @Future(message = "End time must be in the future")
        Instant scheduledEnd,

        @Size(max = 50, message = "Time zone must not exceed 50 characters")
        String timeZone,

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

    public UpdateBookingRequestDto {
        topic = trim(topic);
        studentNotes = trim(studentNotes);
        timeZone = trim(timeZone);
        meetingPlatform = trim(meetingPlatform);
        locationName = trim(locationName);
        addressLine = trim(addressLine);
        city = trim(city);
        stateRegion = trim(stateRegion);
        country = trim(country);
        locationInstructions = trim(locationInstructions);
    }

    @JsonIgnore
    public boolean isReschedule() {
        return scheduledStart != null && scheduledEnd != null;
    }

    @JsonIgnore
    @AssertTrue(message = "To reschedule, provide both scheduledStart and scheduledEnd")
    public boolean isRescheduleComplete() {
        return (scheduledStart == null) == (scheduledEnd == null);
    }

    @JsonIgnore
    @AssertTrue(message = "End time must be after start time")
    public boolean isTimeRangeValid() {
        return !isReschedule() || scheduledEnd.isAfter(scheduledStart);
    }

    @JsonIgnore
    @AssertTrue(message = "A session must last between 15 minutes and 8 hours")
    public boolean isDurationValid() {
        if (!isReschedule() || !scheduledEnd.isAfter(scheduledStart)) {
            return true;
        }
        long minutes = Duration.between(scheduledStart, scheduledEnd).toMinutes();
        return minutes >= CreateBookingRequestDto.MIN_DURATION_MINUTES
                && minutes <= CreateBookingRequestDto.MAX_DURATION_MINUTES;
    }

    @JsonIgnore
    @AssertTrue(message = "Time zone must be a valid IANA zone, e.g. Africa/Douala")
    public boolean isTimeZoneValid() {
        return CreateBookingRequestDto.isValidZone(timeZone);
    }

    @JsonIgnore
    @AssertTrue(message = "Latitude and longitude must be provided together")
    public boolean isCoordinatesComplete() {
        return (latitude == null) == (longitude == null);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}