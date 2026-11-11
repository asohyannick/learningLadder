package com.learningLadder.learningLadder.repository.shoppingCartRepository;

import com.learningLadder.learningLadder.dto.shoppingCartDto.PagedQueryShoppingCartRequestDto;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCart;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCartItem;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ShoppingCartSpecification {

    private ShoppingCartSpecification() {
    }

    public static Specification<ShoppingCart> fromQuery(PagedQueryShoppingCartRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.userId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), query.userId()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.courseId() != null) {
                Join<ShoppingCart, ShoppingCartItem> items = root.join("items");
                predicates.add(cb.equal(items.get("course").get("id"), query.courseId()));
                criteriaQuery.distinct(true);
            }
            if (query.couponCode() != null) {
                predicates.add(cb.equal(root.get("couponCode"), query.couponCode()));
            }
            if (query.minTotal() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("total"), query.minTotal()));
            }
            if (query.maxTotal() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("total"), query.maxTotal()));
            }
            if (query.lastActivityFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("lastActivityAt"), query.lastActivityFrom()));
            }
            if (query.lastActivityTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("lastActivityAt"), query.lastActivityTo()));
            }
            if (query.checkedOutFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("checkedOutAt"), query.checkedOutFrom()));
            }
            if (query.checkedOutTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("checkedOutAt"), query.checkedOutTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}