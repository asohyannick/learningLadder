package com.learningLadder.learningLadder.repository.courseRepository;

import com.learningLadder.learningLadder.dto.courseDto.CoursePagedQueryDto;
import com.learningLadder.learningLadder.entity.course.Course;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class CourseSpecifications {

    private CourseSpecifications() { }

    public static Specification<Course> from(CoursePagedQueryDto q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(q.search())) {
                String like = "%" + escape(q.search().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("shortDescription")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("category")), like, '\\')));
            }
            if (hasText(q.category())) {
                predicates.add(cb.equal(cb.lower(root.<String>get("category")), q.category().trim().toLowerCase()));
            }
            if (hasText(q.language())) {
                predicates.add(cb.equal(cb.lower(root.<String>get("language")), q.language().trim().toLowerCase()));
            }
            if (q.level() != null)     predicates.add(cb.equal(root.get("level"), q.level()));
            if (q.status() != null)    predicates.add(cb.equal(root.get("status"), q.status()));
            if (q.published() != null) predicates.add(cb.equal(root.get("published"), q.published()));
            if (q.minPrice() != null)  predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), q.minPrice()));
            if (q.maxPrice() != null)  predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), q.maxPrice()));

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}