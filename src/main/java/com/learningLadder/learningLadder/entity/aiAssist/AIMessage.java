package com.learningLadder.learningLadder.entity.aiAssist;

import com.learningLadder.learningLadder.enums.aiMessageRole.AIMessageRole;
import com.learningLadder.learningLadder.enums.aiMessageStatus.AIMessageStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "aiMessage")
@Table(
        name = "ai_messages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_message_sequence", columnNames = {"conversation_id", "sequence_number"}),
                @UniqueConstraint(name = "uk_ai_message_idempotency", columnNames = {"conversation_id", "idempotency_key"})
        },
        indexes = {
                @Index(name = "idx_ai_message_conversation", columnList = "conversation_id, sequence_number"),
                @Index(name = "idx_ai_message_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AIMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private AIConversation conversation;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 20)
    private AIMessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AIMessageStatus status = AIMessageStatus.COMPLETED;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "provider_response_id", length = 100)
    private String providerResponseId;

    @Column(name = "finish_reason", length = 30)
    private String finishReason;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "flagged", nullable = false)
    private boolean flagged = false;

    @Column(name = "feedback_rating")
    private Short feedbackRating;

    @Column(name = "feedback_comment", length = 500)
    private String feedbackComment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}