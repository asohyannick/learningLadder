package com.learningLadder.learningLadder.entity.library;

import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity(name = "library")
@Table(
        name = "library",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_library_isbn", columnNames = {"isbn"})
        },
        indexes = {
                @Index(name = "idx_library_title", columnList = "title"),
                @Index(name = "idx_library_author", columnList = "author"),
                @Index(name = "idx_library_category", columnList = "category"),
                @Index(name = "idx_library_course", columnList = "course_id"),
                @Index(name = "idx_library_active_available", columnList = "active, available_copies")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Library {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Column(name = "author", nullable = false, length = 255)
    private String author;

    @Column(name = "isbn", length = 13)
    private String isbn;

    @Column(name = "publisher", length = 150)
    private String publisher;

    @Column(name = "publication_year")
    private Integer publicationYear;

    @Column(name = "edition", length = 50)
    private String edition;

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "cover_image", length = 1024)
    private String coverImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "shelf_location", length = 50)
    private String shelfLocation;

    @Column(name = "total_copies", nullable = false)
    private int totalCopies = 1;

    @Column(name = "available_copies", nullable = false)
    private int availableCopies = 1;

    @Column(name = "borrowable", nullable = false)
    private boolean borrowable = true;

    @Column(name = "loan_period_days", nullable = false)
    private int loanPeriodDays = 14;

    @Column(name = "max_renewals", nullable = false)
    private int maxRenewals = 2;

    @Column(name = "daily_late_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyLateFee = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean canBeBorrowed() {
        return active && borrowable && availableCopies > 0;
    }

    public void checkOut() {
        if (!active || !borrowable) {
            throw new BadRequestException("This book cannot be borrowed");
        }
        if (availableCopies <= 0) {
            throw new BadRequestException("No copies of this book are currently available");
        }
        availableCopies--;
    }

    public void checkIn() {
        if (availableCopies >= totalCopies) {
            throw new BadRequestException("All copies of this book are already on the shelf");
        }
        availableCopies++;
    }
}