package com.learningLadder.learningLadder.entity.jobListing;

import com.learningLadder.learningLadder.enums.employmentType.EmploymentType;
import com.learningLadder.learningLadder.enums.experienceLevel.ExperienceLevel;
import com.learningLadder.learningLadder.enums.jobStatus.JobStatus;
import com.learningLadder.learningLadder.enums.salaryPeriod.SalaryPeriod;
import com.learningLadder.learningLadder.enums.workMode.WorkMode;
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
@Table(name = "job_listings")
@Getter
@Setter
@NoArgsConstructor
public class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "jobPhotoUrl", length = 1024)
    private String jobPhotoUrl;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "reference_code", unique = true, length = 30)
    private String referenceCode;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "responsibilities", columnDefinition = "TEXT")
    private String responsibilities;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "faculty", length = 150)
    private String faculty;

    @Column(name = "department", length = 150)
    private String department;

    @Column(name = "position_title", length = 150)
    private String positionTitle;

    @Column(name = "reports_to", length = 150)
    private String reportsTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 30)
    private WorkMode workMode = WorkMode.ON_SITE;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", length = 30)
    private ExperienceLevel experienceLevel;

    @Column(name = "minimum_years_experience")
    private Integer minimumYearsExperience;

    @Column(name = "minimum_education", length = 100)
    private String minimumEducation;

    @Column(name = "number_of_vacancies", nullable = false)
    private int numberOfVacancies = 1;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "country", length = 100)
    private String country;

    @ElementCollection
    @CollectionTable(name = "job_listing_certifications",
            joinColumns = @JoinColumn(name = "job_listing_id"))
    @Column(name = "certification", nullable = false, length = 150)
    private List<String> certifications = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_listing_skills",
            joinColumns = @JoinColumn(name = "job_listing_id"))
    @Column(name = "skill", nullable = false, length = 100)
    private List<String> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "job_listing_languages",
            joinColumns = @JoinColumn(name = "job_listing_id"))
    @Column(name = "language", nullable = false, length = 50)
    private List<String> languages = new ArrayList<>();

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "job_photo_file_id")
    private UUID jobPhotoFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 30)
    private SalaryPeriod salaryPeriod = SalaryPeriod.MONTHLY;

    @Column(name = "salary_visible", nullable = false)
    private boolean salaryVisible = true;

    @ElementCollection
    @CollectionTable(name = "job_listing_benefits",
            joinColumns = @JoinColumn(name = "job_listing_id"))
    @Column(name = "benefit", nullable = false, length = 150)
    private List<String> benefits = new ArrayList<>();

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Column(name = "expected_start_date")
    private LocalDate expectedStartDate;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "application_instructions", columnDefinition = "TEXT")
    private String applicationInstructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
