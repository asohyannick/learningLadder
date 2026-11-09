package com.learningLadder.learningLadder.repository.jobApplicationRepository;

import com.learningLadder.learningLadder.dto.jobApplicationDto.PagedQueryApplicationRequestDto;
import com.learningLadder.learningLadder.entity.jobApplication.JobApplication;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JobApplicationSpecification {

    private static final char ESCAPE_CHAR = '\\';

    private JobApplicationSpecification() {
    }

    public static Specification<JobApplication> fromQuery(PagedQueryApplicationRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.jobListingId() != null) {
                predicates.add(cb.equal(root.get("jobListing").get("id"), query.jobListingId()));
            }
            if (query.applicantId() != null) {
                predicates.add(cb.equal(root.get("applicant").get("id"), query.applicantId()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.submittedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("submittedAt"), query.submittedFrom()));
            }
            if (query.submittedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("submittedAt"), query.submittedTo()));
            }
            if (query.keyword() != null) {
                String pattern = "%" + escapeLike(query.keyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("lastName")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("email")), pattern, ESCAPE_CHAR)
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