package com.learningLadder.learningLadder.dto.bookingDto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ApproveBookingRequestDto(

        @Schema(description = "Video call link for online and hybrid sessions", example = "https://meet.google.com/abc-defg-hij")
        @Size(max = 500, message = "Meeting link must not exceed 500 characters")
        @Pattern(regexp = "^https://\\S+$", message = "Meeting link must be a valid https:// URL")
        String meetingLink,

        @Schema(description = "Meeting ID, if the platform uses one", example = "845 2231 9087")
        @Size(max = 100, message = "Meeting ID must not exceed 100 characters")
        String meetingId,

        @Schema(description = "Meeting passcode, if any", example = "learn42")
        @Size(max = 100, message = "Meeting passcode must not exceed 100 characters")
        String meetingPasscode,

        @Schema(description = "Note for the student, e.g. what to prepare")
        @Size(max = 2000, message = "Notes must not exceed 2,000 characters")
        String tutorNotes
) {
    public ApproveBookingRequestDto {
        meetingLink = clean(meetingLink);
        meetingId = clean(meetingId);
        meetingPasscode = clean(meetingPasscode);
        tutorNotes = clean(tutorNotes);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}