package com.learningLadder.learningLadder.dto.jobListingDto;

import com.learningLadder.learningLadder.enums.employmentType.EmploymentType;
import com.learningLadder.learningLadder.enums.experienceLevel.ExperienceLevel;
import com.learningLadder.learningLadder.enums.jobStatus.JobStatus;
import com.learningLadder.learningLadder.enums.workMode.WorkMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record JobListingPagedQueryDto(
        @Schema(description = "Keyword matched against title, short description, department and reference code")
        @Size(max = 100) String search,

        @Size(max = 150) String faculty,
        @Size(max = 150) String department,
        @Size(max = 100) String city,
        @Size(max = 100) String country,

        EmploymentType employmentType,
        WorkMode workMode,
        ExperienceLevel experienceLevel,
        JobStatus status,

        @Schema(description = "Only listings still accepting applications (OPEN and not past deadline)")
        Boolean openOnly,

        @Schema(description = "Minimum of the salary range") @DecimalMin("0.00") BigDecimal minSalary,
        @Schema(description = "Maximum of the salary range") @DecimalMin("0.00") BigDecimal maxSalary,

        @Schema(description = "Deadline on or after this date") LocalDate deadlineFrom,
        @Schema(description = "Deadline on or before this date") LocalDate deadlineTo,

        @Schema(example = "0") @Min(0) Integer page,
        @Schema(example = "20") @Min(1) @Max(100) Integer size,

        @Schema(example = "createdAt")
        @Pattern(regexp = "title|applicationDeadline|publishedAt|createdAt|updatedAt|salaryMin|numberOfVacancies",
                message = "sortBy must be one of: title, applicationDeadline, publishedAt, createdAt, updatedAt, salaryMin, numberOfVacancies")
        String sortBy,

        @Schema(example = "desc")
        @Pattern(regexp = "(?i)asc|desc", message = "sortDirection must be asc or desc")
        String sortDirection
) {
    public JobListingPagedQueryDto {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? "desc" : sortDirection.toLowerCase();
    }

    @Schema(hidden = true)
    @AssertTrue(message = "minSalary cannot be greater than maxSalary")
    public boolean isSalaryRangeValid() {
        return minSalary == null || maxSalary == null || minSalary.compareTo(maxSalary) <= 0;
    }

    @Schema(hidden = true)
    @AssertTrue(message = "deadlineFrom cannot be after deadlineTo")
    public boolean isDeadlineRangeValid() {
        return deadlineFrom == null || deadlineTo == null || !deadlineFrom.isAfter(deadlineTo);
    }

    public String cacheKey() {
        return String.join("|",
                lower(search), lower(faculty), lower(department), lower(city), lower(country),
                Objects.toString(employmentType, ""), Objects.toString(workMode, ""),
                Objects.toString(experienceLevel, ""), Objects.toString(status, ""),
                Objects.toString(openOnly, ""), Objects.toString(minSalary, ""), Objects.toString(maxSalary, ""),
                Objects.toString(deadlineFrom, ""), Objects.toString(deadlineTo, ""),
                String.valueOf(page), String.valueOf(size), sortBy, sortDirection);
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}