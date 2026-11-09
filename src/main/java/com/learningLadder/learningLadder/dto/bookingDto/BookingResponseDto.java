package com.learningLadder.learningLadder.dto.bookingDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.bookingStatus.BookingStatus;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BookingResponseDto(

        UUID id,
        String bookingReference,

        UUID studentId,
        UUID tutorId,
        UUID courseId,

        String topic,
        String studentNotes,

        Instant scheduledStart,
        Instant scheduledEnd,
        Integer durationMinutes,
        String timeZone,

        SessionMode sessionMode,
        String meetingPlatform,
        String meetingLink,
        String meetingId,
        String meetingPasscode,
        String locationName,
        String addressLine,
        String city,
        String stateRegion,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        String locationInstructions,

        BigDecimal price,
        String currency,
        PaymentStatus paymentStatus,

        BookingStatus status,
        Instant confirmedAt,
        Instant rejectedAt,
        String rejectionReason,
        Instant cancelledAt,
        UUID cancelledBy,
        String cancellationReason,
        Instant completedAt,
        int rescheduleCount,
        Instant lastRescheduledAt,
        String tutorNotes,

        boolean reminderSent,
        Instant reminderSentAt,

        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public boolean upcoming() {
        return status != null && status.isActive()
                && scheduledStart != null && scheduledStart.isAfter(Instant.now());
    }
}
