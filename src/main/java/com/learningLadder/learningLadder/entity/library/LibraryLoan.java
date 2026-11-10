package com.learningLadder.learningLadder.entity.library;

import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import com.learningLadder.learningLadder.enums.loanStatus.LoanStatus;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Entity(name = "libraryLoan")
@Table(
        name = "library_loans",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_library_loan_reference", columnNames = {"loan_reference"}),
                @UniqueConstraint(name = "uk_library_loan_idempotency_key", columnNames = {"idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_library_loan_book", columnList = "book_id"),
                @Index(name = "idx_library_loan_student_status", columnList = "student_id, status"),
                @Index(name = "idx_library_loan_status_due", columnList = "status, due_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class LibraryLoan {

    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "loan_reference", nullable = false, updatable = false, length = 30)
    private String loanReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, updatable = false)
    private Library book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanStatus status = LoanStatus.RESERVED;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    @Column(name = "borrowed_at")
    private Instant borrowedAt;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "return_requested_at")
    private Instant returnRequestedAt;

    @Column(name = "return_request_note", length = 1000)
    private String returnRequestNote;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "renewal_count", nullable = false)
    private int renewalCount = 0;

    @Column(name = "last_renewed_at")
    private Instant lastRenewedAt;

    @Column(name = "issued_by")
    private UUID issuedBy;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "condition_on_borrow", length = 255)
    private String conditionOnBorrow;

    @Column(name = "condition_on_return", length = 255)
    private String conditionOnReturn;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "late_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "fee_currency", nullable = false, length = 3)
    private String feeCurrency = "USD";

    @Column(name = "fee_paid", nullable = false)
    private boolean feePaid = false;

    @Column(name = "fee_paid_at")
    private Instant feePaidAt;

    @Column(name = "due_reminder_sent", nullable = false)
    private boolean dueReminderSent = false;

    @Column(name = "due_reminder_sent_at")
    private Instant dueReminderSentAt;

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
        if (loanReference == null) {
            String random = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 6).toUpperCase(Locale.ROOT);
            loanReference = "LN-" + REFERENCE_DATE.format(Instant.now()) + "-" + random;
        }
        if (reservedAt == null && status == LoanStatus.RESERVED) {
            reservedAt = Instant.now();
        }
        validateDates();
    }

    @PreUpdate
    private void onUpdate() {
        validateDates();
    }

    private void validateDates() {
        if (borrowedAt != null && dueAt != null && !dueAt.isAfter(borrowedAt)) {
            throw new BadRequestException("Loan due date must be after the borrow date");
        }
        if (borrowedAt != null && returnedAt != null && returnedAt.isBefore(borrowedAt)) {
            throw new BadRequestException("Return date cannot be before the borrow date");
        }
    }

    public boolean isOverdue() {
        return (status == LoanStatus.BORROWED || status == LoanStatus.OVERDUE)
                && dueAt != null && Instant.now().isAfter(dueAt);
    }
}