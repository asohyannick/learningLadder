package com.learningLadder.learningLadder.dto.tutorProfileDto;

import com.learningLadder.learningLadder.enums.tutorStatus.TutorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTutorProfessionalRecordRequestDto(
        TutorStatus status,

        @Schema(description = "Staff-assigned tutor number", example = "TUT-2026-0042")
        @Size(max = 30, message = "Tutor number must not exceed 30 characters")
        @Pattern(regexp = "^[A-Za-z0-9/_-]+$",
                message = "Tutor number may only contain letters, digits, '/', '_' and '-'")
        String tutorNumber,

        @Schema(description = "Date the tutor joined the platform")
        LocalDate joinedDate
) { }