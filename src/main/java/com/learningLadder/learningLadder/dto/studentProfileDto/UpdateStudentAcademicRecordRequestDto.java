package com.learningLadder.learningLadder.dto.studentProfileDto;

import com.learningLadder.learningLadder.enums.enrollmentStatus.EnrollmentStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateStudentAcademicRecordRequestDto(
        EnrollmentStatus enrollmentStatus,

        @DecimalMin(value = "0.00", message = "GPA cannot be negative")
        @DecimalMax(value = "5.00", message = "GPA cannot exceed 5.00")
        @Digits(integer = 1, fraction = 2, message = "GPA allows 2 decimals")
        BigDecimal gpa,

        LocalDate admissionDate,
        LocalDate expectedGraduationDate
) {
    @io.swagger.v3.oas.annotations.media.Schema(hidden = true)
    @AssertTrue(message = "Expected graduation date must be after the admission date")
    public boolean isGraduationDateValid() {
        return admissionDate == null || expectedGraduationDate == null
                || expectedGraduationDate.isAfter(admissionDate);
    }
}