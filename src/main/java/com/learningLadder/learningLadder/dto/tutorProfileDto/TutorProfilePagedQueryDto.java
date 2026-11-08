package com.learningLadder.learningLadder.dto.tutorProfileDto;

import com.learningLadder.learningLadder.enums.tutorStatus.TutorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Objects;

public record TutorProfilePagedQueryDto(
        @Schema(description = "Keyword matched against name, email and tutor number")
        @Size(max = 100) String search,

        @Size(max = 150) String faculty,
        @Size(max = 150) String department,
        @Size(max = 150) String fieldOfSpecialization,

        @Schema(description = "Only tutors who teach this subject (case-insensitive exact match)")
        @Size(max = 150) String subject,

        @Schema(description = "Minimum years of teaching experience")
        @Min(0) @Max(60) Integer minYearsOfExperience,

        TutorStatus status,

        @Schema(description = "Joined on or after this date") LocalDate joinedFrom,
        @Schema(description = "Joined on or before this date") LocalDate joinedTo,

        @Schema(example = "0") @Min(0) Integer page,
        @Schema(example = "20") @Min(1) @Max(100) Integer size,

        @Schema(example = "createdAt")
        @Pattern(regexp = "firstName|lastName|email|tutorNumber|yearsOfExperience|joinedDate|createdAt|updatedAt",
                message = "sortBy must be one of: firstName, lastName, email, tutorNumber, yearsOfExperience, joinedDate, createdAt, updatedAt")
        String sortBy,

        @Schema(example = "desc")
        @Pattern(regexp = "(?i)asc|desc", message = "sortDirection must be asc or desc")
        String sortDirection
) {
    public TutorProfilePagedQueryDto {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? "desc" : sortDirection.toLowerCase();
    }

    @Schema(hidden = true)
    @AssertTrue(message = "joinedFrom cannot be after joinedTo")
    public boolean isJoinedRangeValid() {
        return joinedFrom == null || joinedTo == null || !joinedFrom.isAfter(joinedTo);
    }

    public String cacheKey() {
        return String.join("|",
                lower(search), lower(faculty), lower(department), lower(fieldOfSpecialization), lower(subject),
                Objects.toString(minYearsOfExperience, ""), Objects.toString(status, ""),
                Objects.toString(joinedFrom, ""), Objects.toString(joinedTo, ""),
                String.valueOf(page), String.valueOf(size), sortBy, sortDirection);
    }

    private static String lower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}