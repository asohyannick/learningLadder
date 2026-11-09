package com.learningLadder.learningLadder.dto.jobListingDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.employmentType.EmploymentType;
import com.learningLadder.learningLadder.enums.experienceLevel.ExperienceLevel;
import com.learningLadder.learningLadder.enums.jobStatus.JobStatus;
import com.learningLadder.learningLadder.enums.salaryPeriod.SalaryPeriod;
import com.learningLadder.learningLadder.enums.workMode.WorkMode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobListingResponseDto(
        UUID id,
        String title,
        String slug,
        String referenceCode,
        String jobPhotoUrl,
        String shortDescription,
        String description,
        String responsibilities,
        String requirements,
        String faculty,
        String department,
        String positionTitle,
        String reportsTo,
        EmploymentType employmentType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        Integer minimumYearsExperience,
        String minimumEducation,
        int numberOfVacancies,
        String city,
        String stateRegion,
        String country,
        List<String> certifications,
        List<String> skills,
        List<String> languages,
        List<String> benefits,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        SalaryPeriod salaryPeriod,
        boolean salaryVisible,
        LocalDate applicationDeadline,
        LocalDate expectedStartDate,
        String contactEmail,
        String applicationInstructions,
        JobStatus status,
        Instant publishedAt,
        Instant closedAt,
        UUID postedBy,
        Instant createdAt,
        Instant updatedAt
) implements Serializable { }