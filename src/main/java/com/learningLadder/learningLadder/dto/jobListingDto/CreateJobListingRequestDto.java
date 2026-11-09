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

public record CreateJobListingRequestDto(

        @Schema(example = "Lecturer in Mathematics", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        String title,

        @Schema(description = "Public URL of the listing photo", example = "https://cdn.asoh.io/jobs/maths.jpg")
        @Pattern(regexp = "^https?://\\S{3,1020}$", message = "Job photo URL must start with http:// or https://")
        @Size(max = 1024, message = "Job photo URL must not exceed 1024 characters")
        String jobPhotoUrl,

        @Size(max = 500, message = "Short description must not exceed 500 characters")
        String shortDescription,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Description is required")
        @Size(max = 20000, message = "Description must not exceed 20000 characters")
        String description,

        @Size(max = 10000, message = "Responsibilities must not exceed 10000 characters")
        String responsibilities,

        @Size(max = 10000, message = "Requirements must not exceed 10000 characters")
        String requirements,

        @Size(max = 150, message = "Faculty must not exceed 150 characters") String faculty,
        @Size(max = 150, message = "Department must not exceed 150 characters") String department,
        @Size(max = 150, message = "Position title must not exceed 150 characters") String positionTitle,
        @Size(max = 150, message = "Reports-to must not exceed 150 characters") String reportsTo,

        @Schema(description = "Defaults to FULL_TIME when omitted")
        EmploymentType employmentType,

        @Schema(description = "Defaults to ON_SITE when omitted")
        WorkMode workMode,

        ExperienceLevel experienceLevel,

        @Min(value = 0, message = "Minimum years of experience cannot be negative")
        @Max(value = 60, message = "Minimum years of experience must not exceed 60")
        Integer minimumYearsExperience,

        @Size(max = 100, message = "Minimum education must not exceed 100 characters")
        String minimumEducation,

        @Schema(description = "Defaults to 1 when omitted", example = "2")
        @Min(value = 1, message = "At least one vacancy is required")
        @Max(value = 1000, message = "Vacancies must not exceed 1000")
        Integer numberOfVacancies,

        @Size(max = 100, message = "City must not exceed 100 characters") String city,
        @Size(max = 100, message = "State/region must not exceed 100 characters") String stateRegion,
        @Size(max = 100, message = "Country must not exceed 100 characters") String country,

        @Size(max = 20, message = "At most 20 certifications are allowed")
        List<@NotBlank(message = "Certification cannot be blank")
        @Size(max = 150, message = "Certification must not exceed 150 characters") String> certifications,

        @Size(max = 30, message = "At most 30 skills are allowed")
        List<@NotBlank(message = "Skill cannot be blank")
        @Size(max = 100, message = "Skill must not exceed 100 characters") String> skills,

        @Size(max = 10, message = "At most 10 languages are allowed")
        List<@NotBlank(message = "Language cannot be blank")
        @Size(max = 50, message = "Language must not exceed 50 characters") String> languages,

        @Size(max = 20, message = "At most 20 benefits are allowed")
        List<@NotBlank(message = "Benefit cannot be blank")
        @Size(max = 150, message = "Benefit must not exceed 150 characters") String> benefits,

        @Schema(example = "1500.00")
        @DecimalMin(value = "0.00", message = "Minimum salary cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Salary allows up to 10 digits and 2 decimals")
        BigDecimal salaryMin,

        @Schema(example = "2500.00")
        @DecimalMin(value = "0.00", message = "Maximum salary cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Salary allows up to 10 digits and 2 decimals")
        BigDecimal salaryMax,

        @Schema(description = "ISO 4217 code, defaults to USD", example = "USD")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase ISO code")
        String currency,

        @Schema(description = "Defaults to MONTHLY when omitted")
        SalaryPeriod salaryPeriod,

        @Schema(description = "Show the salary range publicly. Defaults to true")
        Boolean salaryVisible,

        @Schema(example = "2026-12-31")
        @FutureOrPresent(message = "Application deadline cannot be in the past")
        LocalDate applicationDeadline,

        LocalDate expectedStartDate,

        @Email(message = "Contact email must be a valid email address")
        @Size(max = 255, message = "Contact email must not exceed 255 characters")
        String contactEmail,

        @Size(max = 5000, message = "Application instructions must not exceed 5000 characters")
        String applicationInstructions
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