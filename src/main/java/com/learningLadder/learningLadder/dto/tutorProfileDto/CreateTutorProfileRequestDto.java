package com.learningLadder.learningLadder.dto.tutorProfileDto;

import com.learningLadder.learningLadder.enums.gender.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateTutorProfileRequestDto(
        @Schema(example = "Amina", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 255, message = "First name must be between 2 and 255 characters")
        @Pattern(regexp = "^[\\p{L}][\\p{L}\\p{M} '.-]*$",
                message = "First name may only contain letters, spaces, apostrophes, periods and hyphens")
        String firstName,

        @Schema(example = "Nkemelu", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 255, message = "Last name must be between 2 and 255 characters")
        @Pattern(regexp = "^[\\p{L}][\\p{L}\\p{M} '.-]*$",
                message = "Last name may only contain letters, spaces, apostrophes, periods and hyphens")
        String lastName,

        @Schema(description = "Courtesy or academic title", example = "Dr.")
        @Size(max = 30, message = "Title must not exceed 30 characters")
        String title,

        @Schema(example = "1988-03-14")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 100, message = "Nationality must not exceed 100 characters")
        String nationality,

        @Schema(example = "+237670000000")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$", message = "Phone number is not valid")
        String phoneNumber,

        @Schema(description = "Short bio shown on the tutor's profile")
        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Schema(description = "Up to 20 hobbies")
        @Size(max = 20, message = "At most 20 hobbies are allowed")
        List<@NotBlank(message = "Hobby cannot be blank") @Size(max = 100, message = "Hobby must not exceed 100 characters") String> hobbies,

        @Schema(description = "Languages the tutor teaches in, up to 15", example = "[\"English\", \"French\"]")
        @Size(max = 15, message = "At most 15 languages are allowed")
        List<@NotBlank(message = "Language cannot be blank") @Size(max = 100, message = "Language must not exceed 100 characters") String> languages,

        @Size(max = 255, message = "Address line must not exceed 255 characters") String addressLine,
        @Size(max = 100, message = "City must not exceed 100 characters") String city,
        @Size(max = 100, message = "State/region must not exceed 100 characters") String stateRegion,
        @Size(max = 20, message = "Postal code must not exceed 20 characters") String postalCode,
        @Size(max = 100, message = "Country must not exceed 100 characters") String country,

        @Size(max = 150, message = "Faculty must not exceed 150 characters") String faculty,
        @Size(max = 150, message = "Department must not exceed 150 characters") String department,

        @Schema(example = "MSc Computer Science")
        @Size(max = 150, message = "Highest qualification must not exceed 150 characters")
        String highestQualification,

        @Schema(example = "Machine Learning")
        @Size(max = 150, message = "Field of specialization must not exceed 150 characters")
        String fieldOfSpecialization,

        @Schema(description = "Subjects the tutor teaches, up to 30", example = "[\"Calculus\", \"Linear Algebra\"]")
        @Size(max = 30, message = "At most 30 subjects are allowed")
        List<@NotBlank(message = "Subject cannot be blank") @Size(max = 150, message = "Subject must not exceed 150 characters") String> subjects,

        @Schema(description = "Professional certifications, up to 20")
        @Size(max = 20, message = "At most 20 certifications are allowed")
        List<@NotBlank(message = "Certification cannot be blank") @Size(max = 255, message = "Certification must not exceed 255 characters") String> certifications,

        @Schema(description = "Years of teaching experience", example = "5")
        @Min(value = 0, message = "Years of experience cannot be negative")
        @Max(value = 60, message = "Years of experience must not exceed 60")
        Integer yearsOfExperience,

        @Schema(description = "Price per hour, in the currency below", example = "25.00")
        @DecimalMin(value = "0.00", message = "Hourly rate cannot be negative")
        @Digits(integer = 8, fraction = 2, message = "Hourly rate allows up to 8 digits and 2 decimals")
        BigDecimal hourlyRate,

        @Schema(description = "ISO 4217 currency code", example = "XAF")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code such as XAF, NGN or USD")
        String currency,

        @Size(max = 150, message = "Emergency contact name must not exceed 150 characters")
        String emergencyContactName,

        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$", message = "Emergency contact phone is not valid")
        String emergencyContactPhone,

        @Size(max = 50, message = "Relationship must not exceed 50 characters")
        String emergencyContactRelationship,

        @Pattern(regexp = "^https://.{3,250}$", message = "LinkedIn URL must start with https://")
        String linkedinUrl,

        @Pattern(regexp = "^https?://.{3,250}$", message = "Website URL must start with http:// or https://")
        String websiteUrl
) {
    @Schema(hidden = true)
    @AssertTrue(message = "Currency is required when an hourly rate is set")
    public boolean isCurrencyProvidedWithRate() {
        return hourlyRate == null || (currency != null && !currency.isBlank());
    }
}