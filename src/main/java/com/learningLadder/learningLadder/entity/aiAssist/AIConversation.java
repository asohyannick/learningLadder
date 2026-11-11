package com.learningLadder.learningLadder.entity.aiAssist;

import com.learningLadder.learningLadder.entity.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity(name = "aiConversation")
@Table(
        name = "ai_conversations",
        indexes = {
                @Index(name = "idx_ai_conversation_user_last", columnList = "user_id, last_message_at"),
                @Index(name = "idx_ai_conversation_assistant", columnList = "assistant_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AIConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assistant_id", nullable = false, updatable = false)
    private AIAssist assistant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0;

    @Column(name = "total_prompt_tokens", nullable = false)
    private long totalPromptTokens = 0;

    @Column(name = "total_completion_tokens", nullable = false)
    private long totalCompletionTokens = 0;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

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