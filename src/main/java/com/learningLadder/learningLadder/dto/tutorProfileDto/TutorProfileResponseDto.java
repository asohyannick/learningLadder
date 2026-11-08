package com.learningLadder.learningLadder.dto.tutorProfileDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.gender.Gender;
import com.learningLadder.learningLadder.enums.tutorStatus.TutorStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TutorProfileResponseDto(
        UUID id,
        UUID userId,
        String profilePictureUrl,
        String firstName,
        String lastName,
        String email,
        String title,
        LocalDate dateOfBirth,
        Gender gender,
        String nationality,
        String phoneNumber,
        String description,
        List<String> hobbies,
        List<String> languages,
        String addressLine,
        String city,
        String stateRegion,
        String postalCode,
        String country,
        String tutorNumber,
        String faculty,
        String department,
        String highestQualification,
        String fieldOfSpecialization,
        List<String> subjects,
        List<String> certifications,
        Integer yearsOfExperience,
        BigDecimal hourlyRate,
        String currency,
        TutorStatus status,
        LocalDate joinedDate,
        String emergencyContactName,
        String emergencyContactPhone,
        String emergencyContactRelationship,
        String linkedinUrl,
        String websiteUrl,
        Instant createdAt,
        Instant updatedAt
) implements Serializable { }