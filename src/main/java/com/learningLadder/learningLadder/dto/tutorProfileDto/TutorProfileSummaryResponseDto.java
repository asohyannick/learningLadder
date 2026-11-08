package com.learningLadder.learningLadder.dto.tutorProfileDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.tutorStatus.TutorStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorProfileSummaryResponseDto(
        UUID id,
        String profilePictureUrl,
        String title,
        String firstName,
        String lastName,
        String email,
        String tutorNumber,
        String faculty,
        String department,
        String fieldOfSpecialization,
        Integer yearsOfExperience,
        BigDecimal hourlyRate,
        String currency,
        TutorStatus status,
        Instant createdAt
) implements Serializable { }