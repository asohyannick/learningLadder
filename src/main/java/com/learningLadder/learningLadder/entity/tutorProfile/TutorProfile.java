package com.learningLadder.learningLadder.entity.tutorProfile;

import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.gender.Gender;
import com.learningLadder.learningLadder.enums.tutorStatus.TutorStatus;
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

@Entity(
        name = "tutor_profile"
)
@Table(
        name = "tutor_profiles"
)
@Getter
@Setter
@NoArgsConstructor
public class TutorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_picture_id")
    private StoredFile profilePicture;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    private Gender gender;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "tutor_profile_hobbies",
            joinColumns = @JoinColumn(name = "tutor_profile_id"))
    @Column(name = "hobby", nullable = false, length = 100)
    private List<String> hobbies = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tutor_profile_languages",
            joinColumns = @JoinColumn(name = "tutor_profile_id"))
    @Column(name = "language", nullable = false, length = 100)
    private List<String> languages = new ArrayList<>();

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "tutor_number", unique = true, length = 30)
    private String tutorNumber;

    @Column(name = "title", length = 30)
    private String title;

    @Column(name = "faculty", length = 150)
    private String faculty;

    @Column(name = "department", length = 150)
    private String department;

    @Column(name = "highest_qualification", length = 150)
    private String highestQualification;

    @Column(name = "field_of_specialization", length = 150)
    private String fieldOfSpecialization;

    @ElementCollection
    @CollectionTable(name = "tutor_profile_subjects",
            joinColumns = @JoinColumn(name = "tutor_profile_id"))
    @Column(name = "subject", nullable = false, length = 150)
    private List<String> subjects = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "tutor_profile_certifications",
            joinColumns = @JoinColumn(name = "tutor_profile_id"))
    @Column(name = "certification", nullable = false, length = 255)
    private List<String> certifications = new ArrayList<>();

    @Column(name = "years_of_experience")
    private Integer yearsOfExperience;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TutorStatus status = TutorStatus.PENDING_APPROVAL;

    @Column(name = "joined_date")
    private LocalDate joinedDate;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 30)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 50)
    private String emergencyContactRelationship;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "website_url", length = 255)
    private String websiteUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}