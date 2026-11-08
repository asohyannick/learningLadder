package com.learningLadder.learningLadder.entity.course;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.enums.courseLevel.CourseLevel;
import com.learningLadder.learningLadder.enums.courseStatus.CourseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(
        name = "courses"
)
@Table(
        name = "course"
)
@Getter
@Setter
@NoArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "learning_outcomes", columnDefinition = "TEXT")
    private String learningOutcomes;

    @Column(name = "requirements", columnDefinition = "TEXT")
    private String requirements;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_level")
    private CourseLevel level = CourseLevel.ALL_LEVELS;

    @Column(name = "language", length = 50)
    private String language;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // ---------- Pricing ----------

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "discount", precision = 12, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private CourseStatus status = CourseStatus.FREE;

    // ---------- Publishing ----------

    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ---------- Media (files live in MinIO, tracked through StoredFile) ----------

    /** Course cover image (an IMAGE). Replaces the old imageUrl string. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thumbnail_id")
    private StoredFile thumbnail;

    /** Preview / trailer video (a VIDEO). Replaces the old uploadVideo string. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_video_id")
    private StoredFile promoVideo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "syllabus_id")
    private StoredFile syllabus;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CourseMaterial> materials = new ArrayList<>();

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addMaterial(CourseMaterial material) {
        materials.add(material);
        material.setCourse(this);
    }

    public void removeMaterial(CourseMaterial material) {
        materials.remove(material);
        material.setCourse(null);
    }
}
