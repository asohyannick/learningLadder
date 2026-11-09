package com.learningLadder.learningLadder.mappers.bookingMapper;

import com.learningLadder.learningLadder.dto.bookingDto.BookingResponseDto;
import com.learningLadder.learningLadder.dto.bookingDto.CreateBookingRequestDto;
import com.learningLadder.learningLadder.dto.bookingDto.UpdateBookingRequestDto;
import com.learningLadder.learningLadder.entity.booking.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookingReference", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "tutor", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "durationMinutes", ignore = true)
    @Mapping(target = "meetingLink", ignore = true)
    @Mapping(target = "meetingId", ignore = true)
    @Mapping(target = "meetingPasscode", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "rejectedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "rescheduleCount", ignore = true)
    @Mapping(target = "lastRescheduledAt", ignore = true)
    @Mapping(target = "tutorNotes", ignore = true)
    @Mapping(target = "reminderSent", ignore = true)
    @Mapping(target = "reminderSentAt", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Booking toEntity(CreateBookingRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bookingReference", ignore = true)
    @Mapping(target = "student", ignore = true)
    @Mapping(target = "tutor", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "scheduledStart", ignore = true)
    @Mapping(target = "scheduledEnd", ignore = true)
    @Mapping(target = "durationMinutes", ignore = true)
    @Mapping(target = "meetingLink", ignore = true)
    @Mapping(target = "meetingId", ignore = true)
    @Mapping(target = "meetingPasscode", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "rejectedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "cancelledBy", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "rescheduleCount", ignore = true)
    @Mapping(target = "lastRescheduledAt", ignore = true)
    @Mapping(target = "tutorNotes", ignore = true)
    @Mapping(target = "reminderSent", ignore = true)
    @Mapping(target = "reminderSentAt", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateBookingRequestDto dto, @MappingTarget Booking entity);

    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "tutorId", source = "tutor.id")
    @Mapping(target = "courseId", source = "course.id")
    BookingResponseDto toResponse(Booking entity);

    List<BookingResponseDto> toResponseList(List<Booking> entities);
}