package com.learningLadder.learningLadder.dto.jobListingDto;

import com.learningLadder.learningLadder.enums.employmentType.EmploymentType;
import com.learningLadder.learningLadder.enums.experienceLevel.ExperienceLevel;
import com.learningLadder.learningLadder.enums.salaryPeriod.SalaryPeriod;
import com.learningLadder.learningLadder.enums.workMode.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateJobListingRequestDto(

        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        String title,

        @Pattern(regexp = "^https?://\\S{3,1020}$", message = "Job photo URL must start with http:// or https://")
        @Size(max = 1024, message = "Job photo URL must not exceed 1024 characters")
        String jobPhotoUrl,

        @Size(max = 500, message = "Short description must not exceed 500 characters") String shortDescription,
        @Size(max = 20000, message = "Description must not exceed 20000 characters") String description,
        @Size(max = 10000, message = "Responsibilities must not exceed 10000 characters") String responsibilities,
        @Size(max = 10000, message = "Requirements must not exceed 10000 characters") String requirements,

        @Size(max = 150) String faculty,
        @Size(max = 150) String department,
        @Size(max = 150) String positionTitle,
        @Size(max = 150) String reportsTo,

        EmploymentType employmentType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,

        @Min(0) @Max(60) Integer minimumYearsExperience,
        @Size(max = 100) String minimumEducation,
        @Min(value = 1, message = "At least one vacancy is required")
        @Max(value = 1000, message = "Vacancies must not exceed 1000")
        Integer numberOfVacancies,

        @Size(max = 100) String city,
        @Size(max = 100) String stateRegion,
        @Size(max = 100) String country,

        @Size(max = 20, message = "At most 20 certifications are allowed")
        List<@NotBlank @Size(max = 150) String> certifications,

        @Size(max = 30, message = "At most 30 skills are allowed")
        List<@NotBlank @Size(max = 100) String> skills,

        @Size(max = 10, message = "At most 10 languages are allowed")
        List<@NotBlank @Size(max = 50) String> languages,

        @Size(max = 20, message = "At most 20 benefits are allowed")
        List<@NotBlank @Size(max = 150) String> benefits,

        @DecimalMin(value = "0.00", message = "Minimum salary cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Salary allows up to 10 digits and 2 decimals")
        BigDecimal salaryMin,

        @DecimalMin(value = "0.00", message = "Maximum salary cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Salary allows up to 10 digits and 2 decimals")
        BigDecimal salaryMax,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
        String currency,

        SalaryPeriod salaryPeriod,
        Boolean salaryVisible,

        @FutureOrPresent(message = "Application deadline cannot be in the past")
        LocalDate applicationDeadline,

        LocalDate expectedStartDate,

        @Email(message = "Contact email must be a valid email address")
        @Size(max = 255) String contactEmail,

        @Size(max = 5000) String applicationInstructions
) {
    @Schema(hidden = true)
    @AssertTrue(message = "Minimum salary cannot exceed maximum salary")
    public boolean isSalaryRangeValid() {
        return salaryMin == null || salaryMax == null || salaryMin.compareTo(salaryMax) <= 0;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "Expected start date cannot be before the application deadline")
    public boolean isStartDateValid() {
        return applicationDeadline == null || expectedStartDate == null
                || !expectedStartDate.isBefore(applicationDeadline);
    }
}