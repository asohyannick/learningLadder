package com.learningLadder.learningLadder.dto.jobListingDto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.employmentType.EmploymentType;
import com.learningLadder.learningLadder.enums.experienceLevel.ExperienceLevel;
import com.learningLadder.learningLadder.enums.jobStatus.JobStatus;
import com.learningLadder.learningLadder.enums.workMode.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobListingSummaryResponseDto(
        @Schema(description = "Listing ID")
        UUID id,

        @Schema(example = "Lecturer in Mathematics")
        String title,

        @Schema(example = "lecturer-in-mathematics")
        String slug,

        @Schema(example = "JOB-2026-0042")
        String referenceCode,

        String jobPhotoUrl,
        String shortDescription,
        String faculty,
        String department,
        EmploymentType employmentType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        String city,
        String country,
        int numberOfVacancies,

        @Schema(example = "2026-12-31")
        LocalDate applicationDeadline,

        JobStatus status,
        Instant publishedAt
) implements Serializable { }