package com.learningLadder.learningLadder.dto.timeTableDto;

import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import com.learningLadder.learningLadder.enums.timeTableStatus.TimeTableStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Schema(description = "A timetable entry for a course")
public record TimeTableResponseDto(

        @Schema(description = "Timetable entry ID")
        UUID id,

        @Schema(description = "ID of the course this entry belongs to")
        UUID courseId,

        @Schema(description = "Name of the course", example = "Introduction to Algebra")
        String courseName,

        @Schema(description = "Slug of the course", example = "introduction-to-algebra")
        String courseSlug,

        @Schema(example = "Introduction to Algebra - Weekly Lecture")
        String title,

        String description,

        String notes,

        @Schema(description = "Date of the session, or the first date when recurring", example = "2026-11-16")
        LocalDate startDate,

        @Schema(description = "Last date of a recurring entry. Null for a one-off session", example = "2027-02-22")
        LocalDate endDate,

        @Schema(example = "09:00")
        LocalTime startTime,

        @Schema(example = "11:00")
        LocalTime endTime,

        @Schema(description = "Length of each session in minutes", example = "120")
        long durationMinutes,

        @Schema(description = "IANA timezone the times are expressed in", example = "Africa/Douala")
        String timezone,

        @Schema(description = "True when the session repeats weekly")
        boolean recurring,

        @Schema(description = "Day the session repeats on. Null for a one-off session", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(example = "HYBRID")
        SessionMode sessionMode,

        @Schema(example = "Block B, Room 204")
        String location,

        @Schema(example = "https://meet.example.com/algebra-101")
        String meetingUrl,

        @Schema(description = "Maximum number of learners. Null when unlimited", example = "40")
        Integer capacity,

        @Schema(example = "SCHEDULED")
        TimeTableStatus status,

        @Schema(description = "ID of the user who created the entry")
        UUID createdBy,

        Instant createdAt,

        Instant updatedAt
) { }