package com.learningLadder.learningLadder.repository.aiAssistRepository;

import com.learningLadder.learningLadder.entity.aiAssist.AIMessage;
import com.learningLadder.learningLadder.enums.aiMessageRole.AIMessageRole;
import com.learningLadder.learningLadder.enums.aiMessageStatus.AIMessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AIMessageRepository extends JpaRepository<AIMessage, UUID>, JpaSpecificationExecutor<AIMessage> {

    @Query("select coalesce(max(m.sequenceNumber), 0) from aiMessage m where m.conversation.id = :conversationId")
    int findMaxSequenceNumber(@Param("conversationId") UUID conversationId);

    List<AIMessage> findByConversation_IdAndRoleInAndStatusAndFlaggedFalse(
            UUID conversationId, Collection<AIMessageRole> roles, AIMessageStatus status, Pageable pageable);

    List<AIMessage> findByConversation_IdAndSequenceNumberBetweenAndRoleInAndStatusOrderBySequenceNumberAsc(
            UUID conversationId, int fromSequence, int toSequence,
            Collection<AIMessageRole> roles, AIMessageStatus status);

    long countByConversation_User_IdAndConversation_Assistant_IdAndRoleAndCreatedAtGreaterThanEqual(
            UUID userId, UUID assistantId, AIMessageRole role, Instant since);

    boolean existsByConversation_IdAndStatusInAndUpdatedAtAfter(
            UUID conversationId, Collection<AIMessageStatus> statuses, Instant updatedAfter);

    @EntityGraph(attributePaths = "conversation")
    Optional<AIMessage> findWithConversationById(UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from aiMessage m where m.conversation.id = :conversationId")
    int deleteByConversationId(@Param("conversationId") UUID conversationId);
}