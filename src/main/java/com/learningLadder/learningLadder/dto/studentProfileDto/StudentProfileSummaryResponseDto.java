package com.learningLadder.learningLadder.dto.studentProfileDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.enrollmentStatus.EnrollmentStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudentProfileSummaryResponseDto(
        UUID id,
        String profilePictureUrl,
        String firstName,
        String lastName,
        String email,
        String studentNumber,
        String faculty,
        String department,
        String programOfStudy,
        Integer currentLevel,
        EnrollmentStatus enrollmentStatus,
        Instant createdAt
) implements Serializable { }