package com.learningLadder.learningLadder.repository.aiAssistRepository;

import com.learningLadder.learningLadder.entity.aiAssist.AIConversation;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface AIConversationRepository
        extends JpaRepository<AIConversation, UUID>, JpaSpecificationExecutor<AIConversation> {

    @Override
    @EntityGraph(attributePaths = "assistant")
    @NonNull
    Page<AIConversation> findAll(@NonNull Specification<AIConversation> spec, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = "assistant")
    Optional<AIConversation> findWithAssistantById(UUID id);

    boolean existsByAssistant_Id(UUID assistantId);
}