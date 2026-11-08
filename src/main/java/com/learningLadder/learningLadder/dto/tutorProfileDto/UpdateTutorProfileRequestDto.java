package com.learningLadder.learningLadder.dto.tutorProfileDto;

import com.learningLadder.learningLadder.enums.gender.Gender;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateTutorProfileRequestDto(
        @Size(min = 2, max = 255, message = "First name must be between 2 and 255 characters")
        @Pattern(regexp = "^[\\p{L}][\\p{L}\\p{M} '.-]*$", message = "First name contains invalid characters")
        String firstName,

        @Size(min = 2, max = 255, message = "Last name must be between 2 and 255 characters")
        @Pattern(regexp = "^[\\p{L}][\\p{L}\\p{M} '.-]*$", message = "Last name contains invalid characters")
        String lastName,

        @Size(max = 30) String title,

        @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 100) String nationality,
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$", message = "Phone number is not valid") String phoneNumber,
        @Size(max = 5000) String description,

        @Size(max = 20, message = "At most 20 hobbies are allowed")
        List<@NotBlank @Size(max = 100) String> hobbies,

        @Size(max = 15, message = "At most 15 languages are allowed")
        List<@NotBlank @Size(max = 100) String> languages,

        @Size(max = 255) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String stateRegion,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String country,

        @Size(max = 150) String faculty,
        @Size(max = 150) String department,
        @Size(max = 150) String highestQualification,
        @Size(max = 150) String fieldOfSpecialization,

        @Size(max = 30, message = "At most 30 subjects are allowed")
        List<@NotBlank @Size(max = 150) String> subjects,

        @Size(max = 20, message = "At most 20 certifications are allowed")
        List<@NotBlank @Size(max = 255) String> certifications,

        @Min(value = 0, message = "Years of experience cannot be negative")
        @Max(value = 60, message = "Years of experience must not exceed 60")
        Integer yearsOfExperience,

        @DecimalMin(value = "0.00", message = "Hourly rate cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Hourly rate allows up to 8 digits and 2 decimals")
        BigDecimal hourlyRate,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code such as XAF, NGN or USD")
        String currency,

        @Size(max = 150) String emergencyContactName,
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$", message = "Emergency contact phone is not valid") String emergencyContactPhone,
        @Size(max = 50) String emergencyContactRelationship,

        @Pattern(regexp = "^https://.{3,250}$", message = "LinkedIn URL must start with https://") String linkedinUrl,
        @Pattern(regexp = "^https?://.{3,250}$", message = "Website URL must start with http:// or https://") String websiteUrl
) { }