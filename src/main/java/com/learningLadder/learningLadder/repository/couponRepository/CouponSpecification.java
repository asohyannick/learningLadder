package com.learningLadder.learningLadder.repository.couponRepository;

import com.learningLadder.learningLadder.dto.couponDto.PagedQueryCouponRequestDto;
import com.learningLadder.learningLadder.entity.coupon.Coupon;
import com.learningLadder.learningLadder.enums.couponStatus.CouponStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CouponSpecification {

    private CouponSpecification() {
    }

    public static Specification<Coupon> fromQuery(PagedQueryCouponRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.courseId() != null) {
                predicates.add(cb.equal(root.get("course").get("id"), query.courseId()));
            }
            if (query.assignedUserId() != null) {
                predicates.add(cb.equal(root.get("assignedUser").get("id"), query.assignedUserId()));
            }
            if (query.createdBy() != null) {
                predicates.add(cb.equal(root.get("createdBy"), query.createdBy()));
            }
            if (Boolean.TRUE.equals(query.usableOnly())) {
                Instant now = Instant.now();
                predicates.add(cb.equal(root.get("status"), CouponStatus.ACTIVE));
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("startsAt"), now));
                predicates.add(cb.greaterThan(root.<Instant>get("expiresAt"), now));
                predicates.add(cb.or(
                        cb.isNull(root.get("maxRedemptions")),
                        cb.lessThan(root.<Integer>get("redemptionCount"), root.<Integer>get("maxRedemptions"))
                ));
            }
            if (query.expiresFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("expiresAt"), query.expiresFrom()));
            }
            if (query.expiresTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("expiresAt"), query.expiresTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}