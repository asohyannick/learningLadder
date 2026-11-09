package com.learningLadder.learningLadder.repository.bookingRepository;

import com.learningLadder.learningLadder.dto.bookingDto.PagedQueryBookingRequestDto;
import com.learningLadder.learningLadder.entity.booking.Booking;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BookingSpecification {

    private static final char ESCAPE_CHAR = '\\';

    private BookingSpecification() {
    }

    public static Specification<Booking> fromQuery(PagedQueryBookingRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.tutorId() != null) {
                predicates.add(cb.equal(root.get("tutor").get("id"), query.tutorId()));
            }
            if (query.studentId() != null) {
                predicates.add(cb.equal(root.get("student").get("id"), query.studentId()));
            }
            if (query.courseId() != null) {
                predicates.add(cb.equal(root.get("course").get("id"), query.courseId()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.sessionMode() != null) {
                predicates.add(cb.equal(root.get("sessionMode"), query.sessionMode()));
            }
            if (query.startFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("scheduledStart"), query.startFrom()));
            }
            if (query.startTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("scheduledStart"), query.startTo()));
            }
            if (query.keyword() != null) {
                String pattern = "%" + escapeLike(query.keyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("topic")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("bookingReference")), pattern, ESCAPE_CHAR)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}