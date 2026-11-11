package com.learningLadder.learningLadder.entity.aiAssist;

import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.enums.aiProvider.AIProvider;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity(name = "ai_assist")
@Table(
        name = "ai_assist",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_assist_name", columnNames = {"name"}),
                @UniqueConstraint(name = "uk_ai_assist_slug", columnNames = {"slug"})
        },
        indexes = {
                @Index(name = "idx_ai_assist_active", columnList = "active"),
                @Index(name = "idx_ai_assist_course", columnList = "course_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AIAssist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, length = 100)
    private String slug;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "logo", length = 1024)
    private String logo;

    @Column(name = "welcome_message", length = 1000)
    private String welcomeMessage;

    @Column(name = "purpose", length = 50)
    private String purpose;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private AIProvider provider = AIProvider.GROQ;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "temperature", nullable = false, precision = 3, scale = 2)
    private BigDecimal temperature = new BigDecimal("0.70");

    @Column(name = "top_p", nullable = false, precision = 3, scale = 2)
    private BigDecimal topP = new BigDecimal("1.00");

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens = 1024;

    @Column(name = "context_window_messages", nullable = false)
    private int contextWindowMessages = 20;

    @Column(name = "streaming_enabled", nullable = false)
    private boolean streamingEnabled = true;

    @Column(name = "daily_message_limit_per_user", nullable = false)
    private int dailyMessageLimitPerUser = 50;

    @Column(name = "max_input_characters", nullable = false)
    private int maxInputCharacters = 4000;

    @Column(name = "moderation_enabled", nullable = false)
    private boolean moderationEnabled = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

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