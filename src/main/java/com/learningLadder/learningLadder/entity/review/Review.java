package com.learningLadder.learningLadder.entity.review;

import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.reviewStatus.ReviewStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity(
        name = "review"
)
@Table(
        name = "review",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_course_student", columnNames = {"course_id", "student_id"})
        },
        indexes = {
                @Index(name = "idx_review_course", columnList = "course_id"),
                @Index(name = "idx_review_student", columnList = "student_id"),
                @Index(name = "idx_review_course_status", columnList = "course_id, status"),
                @Index(name = "idx_review_rating", columnList = "rating")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private User student;

    @Column(name = "profilePic")
    private String profilePic;

    @Min(1)
    @Max(5)
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "title", length = 150)
    private String title;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "verified_enrollment", nullable = false)
    private boolean verifiedEnrollment = false;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount = 0;

    @Column(name = "moderation_note", length = 500)
    private String moderationNote;

    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "tutor_reply", columnDefinition = "TEXT")
    private String tutorReply;

    @Column(name = "replied_by")
    private UUID repliedBy;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 100)
    private String idempotencyKey;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
