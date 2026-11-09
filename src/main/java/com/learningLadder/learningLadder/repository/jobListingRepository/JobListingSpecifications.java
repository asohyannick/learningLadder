package com.learningLadder.learningLadder.repository.jobListingRepository;

import com.learningLadder.learningLadder.dto.jobListingDto.JobListingPagedQueryDto;
import com.learningLadder.learningLadder.entity.jobListing.JobListing;
import com.learningLadder.learningLadder.enums.jobStatus.JobStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class JobListingSpecifications {

    private JobListingSpecifications() { }

    public static Specification<JobListing> from(JobListingPagedQueryDto q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();

            if (hasText(q.search())) {
                String like = "%" + escape(q.search().trim().toLowerCase()) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("title")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("shortDescription")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("department")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("referenceCode")), like, '\\')));
            }
            if (hasText(q.faculty()))    p.add(cb.equal(cb.lower(root.<String>get("faculty")), q.faculty().trim().toLowerCase()));
            if (hasText(q.department())) p.add(cb.equal(cb.lower(root.<String>get("department")), q.department().trim().toLowerCase()));
            if (hasText(q.city()))       p.add(cb.equal(cb.lower(root.<String>get("city")), q.city().trim().toLowerCase()));
            if (hasText(q.country()))    p.add(cb.equal(cb.lower(root.<String>get("country")), q.country().trim().toLowerCase()));

            if (q.employmentType() != null)  p.add(cb.equal(root.get("employmentType"), q.employmentType()));
            if (q.workMode() != null)        p.add(cb.equal(root.get("workMode"), q.workMode()));
            if (q.experienceLevel() != null) p.add(cb.equal(root.get("experienceLevel"), q.experienceLevel()));
            if (q.status() != null)          p.add(cb.equal(root.get("status"), q.status()));

            if (Boolean.TRUE.equals(q.openOnly())) {
                p.add(cb.equal(root.get("status"), JobStatus.OPEN));
                p.add(cb.or(
                        cb.isNull(root.get("applicationDeadline")),
                        cb.greaterThanOrEqualTo(root.<LocalDate>get("applicationDeadline"), LocalDate.now())));
            }

            if (q.minSalary() != null || q.maxSalary() != null) {
                p.add(cb.isTrue(root.get("salaryVisible")));
                if (q.minSalary() != null) {
                    p.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("salaryMax"), q.minSalary()));
                }
                if (q.maxSalary() != null) {
                    p.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("salaryMin"), q.maxSalary()));
                }
            }

            if (q.deadlineFrom() != null) p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("applicationDeadline"), q.deadlineFrom()));
            if (q.deadlineTo() != null)   p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("applicationDeadline"), q.deadlineTo()));

            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static boolean hasText(String s) { return s != null && !s.isBlank(); }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}