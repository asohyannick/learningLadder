package com.learningLadder.learningLadder.entity.jobApplication;

import com.learningLadder.learningLadder.entity.jobListing.JobListing;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.applicationStatus.ApplicationStatus;
import com.learningLadder.learningLadder.enums.salaryPeriod.SalaryPeriod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "job_applications",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_job_application_applicant", columnNames = {"applicant_id"}),
                @UniqueConstraint(name = "uk_job_application_idempotency_key", columnNames = {"idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_job_application_job", columnList = "job_listing_id"),
                @Index(name = "idx_job_application_status", columnList = "status"),
                @Index(name = "idx_job_application_job_status", columnList = "job_listing_id, status"),
                @Index(name = "idx_job_application_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_listing_id", nullable = false)
    private JobListing jobListing;

    @Column(name = "profilePic", nullable = false,  length = 1024)
    private String profilePic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
    private User applicant;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "current_job_title", length = 150)
    private String currentJobTitle;

    @Column(name = "current_employer", length = 150)
    private String currentEmployer;

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "highest_education", length = 100)
    private String highestEducation;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(name = "expected_salary", precision = 12, scale = 2)
    private BigDecimal expectedSalary;

    @Column(name = "expected_salary_currency", length = 3)
    private String expectedSalaryCurrency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_salary_period", length = 30)
    private SalaryPeriod expectedSalaryPeriod = SalaryPeriod.MONTHLY;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private StoredFile resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_letter_file_id")
    private StoredFile coverLetterFile;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_application_documents",
            joinColumns = @JoinColumn(name = "job_application_id"),
            inverseJoinColumns = @JoinColumn(name = "stored_file_id"))
    private List<StoredFile> supportingDocuments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "interview_scheduled_at")
    private Instant interviewScheduledAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }
}