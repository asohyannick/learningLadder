package com.learningLadder.learningLadder.dto.studentProfileDto;

import com.learningLadder.learningLadder.enums.gender.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public record CreateStudentProfileRequestDto(
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

        @Schema(example = "2005-03-14")
        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        Gender gender,

        @Size(max = 100, message = "Nationality must not exceed 100 characters")
        String nationality,

        @Schema(example = "+237670000000")
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,30}$", message = "Phone number is not valid")
        String phoneNumber,

        @Size(max = 5000, message = "Description must not exceed 5000 characters")
        String description,

        @Schema(description = "Up to 20 hobbies")
        @Size(max = 20, message = "At most 20 hobbies are allowed")
        List<@NotBlank(message = "Hobby cannot be blank") @Size(max = 100, message = "Hobby must not exceed 100 characters") String> hobbies,

        @Size(max = 255, message = "Address line must not exceed 255 characters") String addressLine,
        @Size(max = 100, message = "City must not exceed 100 characters") String city,
        @Size(max = 100, message = "State/region must not exceed 100 characters") String stateRegion,
        @Size(max = 20, message = "Postal code must not exceed 20 characters") String postalCode,
        @Size(max = 100, message = "Country must not exceed 100 characters") String country,

        @Size(max = 150, message = "Faculty must not exceed 150 characters") String faculty,
        @Size(max = 150, message = "Department must not exceed 150 characters") String department,
        @Size(max = 150, message = "Program of study must not exceed 150 characters") String programOfStudy,

        @Schema(description = "Year of study", example = "1")
        @Min(value = 1, message = "Level must be at least 1")
        @Max(value = 10, message = "Level must not exceed 10")
        Integer currentLevel,

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
) { }