package com.learningLadder.learningLadder.entity.courseEnrollment;

import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "courseEnrollment")
@Table(
        name = "course_enrollments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_course_enrollment_user_course", columnNames = {"user_id", "course_id"})
        },
        indexes = {
                @Index(name = "idx_course_enrollment_user_active", columnList = "user_id, active"),
                @Index(name = "idx_course_enrollment_course", columnList = "course_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class CourseEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false, updatable = false)
    private Course course;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}