package com.learningLadder.learningLadder.dto.studentProfileDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.enrollmentStatus.EnrollmentStatus;
import com.learningLadder.learningLadder.enums.gender.Gender;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StudentProfileResponseDto(
        UUID id,
        UUID userId,
        String profilePictureUrl,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        Gender gender,
        String nationality,
        String phoneNumber,
        String description,
        List<String> hobbies,
        String addressLine,
        String city,
        String stateRegion,
        String postalCode,
        String country,
        String studentNumber,
        String faculty,
        String department,
        String programOfStudy,
        Integer currentLevel,
        EnrollmentStatus enrollmentStatus,
        LocalDate admissionDate,
        LocalDate expectedGraduationDate,
        BigDecimal gpa,
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelationship,
        String linkedinUrl,
        String websiteUrl,
        Instant createdAt,
        Instant updatedAt
) implements Serializable { }