package com.learningLadder.learningLadder.dto.jobApplicationDto;

import com.learningLadder.learningLadder.enums.salaryPeriod.SalaryPeriod;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateJobApplicationRequestDto(

        @Size(min = 1, max = 1024, message = "Profile picture must be between 1 and 1024 characters")
        String profilePic,

        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName,

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$", message = "Phone number format is invalid")
        String phoneNumber,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Size(max = 100, message = "Nationality must not exceed 100 characters")
        String nationality,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "State/region must not exceed 100 characters")
        String stateRegion,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        @Size(max = 150, message = "Current job title must not exceed 150 characters")
        String currentJobTitle,

        @Size(max = 150, message = "Current employer must not exceed 150 characters")
        String currentEmployer,

        @Min(value = 0, message = "Years of experience cannot be negative")
        @Max(value = 60, message = "Years of experience must not exceed 60")
        Integer yearsOfExperience,

        @Size(max = 100, message = "Highest education must not exceed 100 characters")
        String highestEducation,

        @URL(message = "LinkedIn URL must be a valid URL")
        @Size(max = 500, message = "LinkedIn URL must not exceed 500 characters")
        String linkedinUrl,

        @URL(message = "Portfolio URL must be a valid URL")
        @Size(max = 500, message = "Portfolio URL must not exceed 500 characters")
        String portfolioUrl,

        @DecimalMin(value = "0.00", message = "Expected salary cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Expected salary must have at most 10 digits and 2 decimals")
        BigDecimal expectedSalary,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g. USD, XAF)")
        String expectedSalaryCurrency,

        SalaryPeriod expectedSalaryPeriod,

        @FutureOrPresent(message = "Available-from date cannot be in the past")
        LocalDate availableFrom,

        @Min(value = 0, message = "Notice period cannot be negative")
        @Max(value = 365, message = "Notice period must not exceed 365 days")
        Integer noticePeriodDays,

        @Size(max = 10_000, message = "Cover letter must not exceed 10,000 characters")
        String coverLetter,

        UUID resumeId,

        UUID coverLetterFileId,

        @Size(max = 10, message = "You can attach at most 10 supporting documents")
        List<@NotNull UUID> supportingDocumentIds
) {

    public UpdateJobApplicationRequestDto {
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        email = email == null ? null : email.trim().toLowerCase();
        profilePic = profilePic == null ? null : profilePic.trim();
        expectedSalaryCurrency = expectedSalaryCurrency == null ? null : expectedSalaryCurrency.trim().toUpperCase();
        supportingDocumentIds = supportingDocumentIds == null ? null : List.copyOf(supportingDocumentIds);
    }
}