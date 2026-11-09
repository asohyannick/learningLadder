package com.learningLadder.learningLadder.entity.booking;

import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import com.learningLadder.learningLadder.entity.tutorProfile.TutorProfile;
import com.learningLadder.learningLadder.enums.bookingStatus.BookingStatus;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.sessionMode.SessionMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Entity(name = "booking")
@Table(
        name = "booking",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_booking_reference", columnNames = {"booking_reference"}),
                @UniqueConstraint(name = "uk_booking_idempotency_key", columnNames = {"idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_booking_tutor_start", columnList = "tutor_id, scheduled_start"),
                @Index(name = "idx_booking_student_start", columnList = "student_id, scheduled_start"),
                @Index(name = "idx_booking_course", columnList = "course_id"),
                @Index(name = "idx_booking_status", columnList = "status"),
                @Index(name = "idx_booking_status_start", columnList = "status, scheduled_start")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_reference", nullable = false, updatable = false, length = 30)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private TutorProfile tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "student_notes", columnDefinition = "TEXT")
    private String studentNotes;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "time_zone", nullable = false, length = 50)
    private String timeZone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(name = "session_mode", nullable = false, length = 30)
    private SessionMode sessionMode;

    @Column(name = "meeting_platform", length = 50)
    private String meetingPlatform;

    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    @Column(name = "meeting_id", length = 100)
    private String meetingId;

    @Column(name = "meeting_passcode", length = 100)
    private String meetingPasscode;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "location_instructions", length = 500)
    private String locationInstructions;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 30)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount = 0;

    @Column(name = "last_rescheduled_at")
    private Instant lastRescheduledAt;

    @Column(name = "tutor_notes", length = 2000)
    private String tutorNotes;

    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent = false;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void onCreate() {
        if (bookingReference == null) {
            String random = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 6).toUpperCase(Locale.ROOT);
            bookingReference = "BK-" + REFERENCE_DATE.format(Instant.now()) + "-" + random;
        }
        computeDuration();
    }

    @PreUpdate
    private void onUpdate() {
        computeDuration();
    }

    private void computeDuration() {
        if (scheduledStart != null && scheduledEnd != null) {
            if (!scheduledEnd.isAfter(scheduledStart)) {
                throw new IllegalStateException("Booking end time must be after its start time");
            }
            durationMinutes = (int) Duration.between(scheduledStart, scheduledEnd).toMinutes();
        }
    }
}