package com.learningLadder.learningLadder.dto.jobApplicationDto;

import com.learningLadder.learningLadder.enums.applicationStatus.ApplicationStatus;
import com.learningLadder.learningLadder.enums.salaryPeriod.SalaryPeriod;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobApplicationResponseDto(

        UUID id,

        String profilePic,
        UUID jobListingId,
        UUID applicantId,

        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth,
        String nationality,

        String city,
        String stateRegion,
        String country,

        String currentJobTitle,
        String currentEmployer,
        Integer yearsOfExperience,
        String highestEducation,
        String linkedinUrl,
        String portfolioUrl,

        BigDecimal expectedSalary,
        String expectedSalaryCurrency,
        SalaryPeriod expectedSalaryPeriod,
        LocalDate availableFrom,
        Integer noticePeriodDays,

        String coverLetter,
        String uploadCV,
        List<String> supportingDocuments,
        List<String> supportingDocumentUrls,

        ApplicationStatus status,
        Instant submittedAt,
        UUID reviewedBy,
        Instant reviewedAt,
        String reviewNote,
        String rejectionReason,
        Instant interviewScheduledAt,
        Instant withdrawnAt,
        boolean consentGiven,

        Instant createdAt,
        Instant updatedAt
) {

    public JobApplicationResponseDto {
        supportingDocuments = supportingDocuments == null ? List.of() : List.copyOf(supportingDocuments);
        supportingDocumentUrls = supportingDocumentUrls == null ? List.of() : List.copyOf(supportingDocumentUrls);
    }

    public String fullName() {
        return (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
    }

    public JobApplicationResponseDto withLinks(String profilePicUrl, String cvUrl, List<String> documentUrls) {
        return new JobApplicationResponseDto(
                id, profilePicUrl, jobListingId, applicantId,
                firstName, lastName, email, phoneNumber, dateOfBirth, nationality,
                city, stateRegion, country,
                currentJobTitle, currentEmployer, yearsOfExperience, highestEducation, linkedinUrl, portfolioUrl,
                expectedSalary, expectedSalaryCurrency, expectedSalaryPeriod, availableFrom, noticePeriodDays,
                coverLetter, cvUrl, supportingDocuments, documentUrls,
                status, submittedAt, reviewedBy, reviewedAt, reviewNote, rejectionReason,
                interviewScheduledAt, withdrawnAt, consentGiven,
                createdAt, updatedAt);
    }
}