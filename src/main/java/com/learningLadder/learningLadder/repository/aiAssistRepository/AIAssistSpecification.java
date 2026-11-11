package com.learningLadder.learningLadder.repository.aiAssistRepository;

import com.learningLadder.learningLadder.dto.aiAssistDto.PagedQueryAIAssistRequestDto;
import com.learningLadder.learningLadder.entity.aiAssist.AIAssist;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AIAssistSpecification {

    private static final char ESCAPE_CHAR = '\\';

    private AIAssistSpecification() {
    }

    public static Specification<AIAssist> fromQuery(PagedQueryAIAssistRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.keyword() != null) {
                String pattern = "%" + escapeLike(query.keyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("slug")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("description")), pattern, ESCAPE_CHAR)
                ));
            }
            if (query.purpose() != null) {
                predicates.add(cb.equal(root.get("purpose"), query.purpose()));
            }
            if (query.provider() != null) {
                predicates.add(cb.equal(root.get("provider"), query.provider()));
            }
            if (query.courseId() != null) {
                predicates.add(cb.equal(root.get("course").get("id"), query.courseId()));
            }
            if (query.active() != null) {
                predicates.add(cb.equal(root.get("active"), query.active()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}