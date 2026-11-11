package com.learningLadder.learningLadder.repository.aiAssistRepository;

import com.learningLadder.learningLadder.dto.aiChatDto.PagedQueryAIConversationRequestDto;
import com.learningLadder.learningLadder.entity.aiAssist.AIConversation;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AIConversationSpecification {

    private static final char ESCAPE_CHAR = '\\';

    private AIConversationSpecification() {
    }

    public static Specification<AIConversation> fromQuery(PagedQueryAIConversationRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.assistantId() != null) {
                predicates.add(cb.equal(root.get("assistant").get("id"), query.assistantId()));
            }
            if (query.userId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), query.userId()));
            }
            if (query.keyword() != null) {
                String pattern = "%" + escapeLike(query.keyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern, ESCAPE_CHAR));
            }
            if (query.pinned() != null) {
                predicates.add(cb.equal(root.get("pinned"), query.pinned()));
            }
            if (query.archived() != null) {
                predicates.add(cb.equal(root.get("archived"), query.archived()));
            }
            if (query.lastMessageFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("lastMessageAt"), query.lastMessageFrom()));
            }
            if (query.lastMessageTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("lastMessageAt"), query.lastMessageTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}