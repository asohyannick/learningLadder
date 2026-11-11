package com.learningLadder.learningLadder.repository.aiAssistRepository;

import com.learningLadder.learningLadder.dto.aiChatDto.PagedQueryAIMessageRequestDto;
import com.learningLadder.learningLadder.entity.aiAssist.AIMessage;
import com.learningLadder.learningLadder.enums.aiMessageRole.AIMessageRole;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AIMessageSpecification {

    private AIMessageSpecification() {
    }

    public static Specification<AIMessage> forConversation(UUID conversationId, PagedQueryAIMessageRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("conversation").get("id"), conversationId));
            predicates.add(cb.notEqual(root.get("role"), AIMessageRole.SYSTEM));

            if (query.role() != null) {
                predicates.add(cb.equal(root.get("role"), query.role()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (Boolean.TRUE.equals(query.flaggedOnly())) {
                predicates.add(cb.isTrue(root.get("flagged")));
            }
            if (query.feedbackRating() != null) {
                predicates.add(cb.equal(root.get("feedbackRating"), query.feedbackRating()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}