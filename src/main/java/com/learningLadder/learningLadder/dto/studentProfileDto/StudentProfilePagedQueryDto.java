package com.learningLadder.learningLadder.dto.studentProfileDto;

import com.learningLadder.learningLadder.enums.enrollmentStatus.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Objects;

public record StudentProfilePagedQueryDto(
        @Schema(description = "Keyword matched against name, email and student number")
        @Size(max = 100) String search,

        @Size(max = 150) String faculty,
        @Size(max = 150) String department,
        @Size(max = 150) String programOfStudy,
        @Min(1) @Max(10) Integer currentLevel,
        EnrollmentStatus enrollmentStatus,

        @Schema(description = "Admitted on or after this date") LocalDate admittedFrom,
        @Schema(description = "Admitted on or before this date") LocalDate admittedTo,

        @Schema(example = "0") @Min(0) Integer page,
        @Schema(example = "20") @Min(1) @Max(100) Integer size,

        @Schema(example = "createdAt")
        @Pattern(regexp = "firstName|lastName|email|studentNumber|currentLevel|admissionDate|createdAt|updatedAt",
                message = "sortBy must be one of: firstName, lastName, email, studentNumber, currentLevel, admissionDate, createdAt, updatedAt")
        String sortBy,

        @Schema(example = "desc")
        @Pattern(regexp = "(?i)asc|desc", message = "sortDirection must be asc or desc")
        String sortDirection
) {
    public StudentProfilePagedQueryDto {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? "desc" : sortDirection.toLowerCase();
    }

    @Schema(hidden = true)
    @AssertTrue(message = "admittedFrom cannot be after admittedTo")
    public boolean isAdmissionRangeValid() {
        return admittedFrom == null || admittedTo == null || !admittedFrom.isAfter(admittedTo);
    }

    public String cacheKey() {
        return String.join("|",
                lower(search), lower(faculty), lower(department), lower(programOfStudy),
                Objects.toString(currentLevel, ""), Objects.toString(enrollmentStatus, ""),
                Objects.toString(admittedFrom, ""), Objects.toString(admittedTo, ""),
                String.valueOf(page), String.valueOf(size), sortBy, sortDirection);
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}