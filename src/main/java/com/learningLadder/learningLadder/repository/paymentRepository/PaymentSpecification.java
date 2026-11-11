package com.learningLadder.learningLadder.repository.paymentRepository;

import com.learningLadder.learningLadder.dto.paymentDto.PagedQueryPaymentRequestDto;
import com.learningLadder.learningLadder.entity.payment.Payment;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.paymentType.PaymentType;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PaymentSpecification {

    private static final char ESCAPE_CHAR = '\\';

    private PaymentSpecification() {
    }

    public static Specification<Payment> fromQuery(PagedQueryPaymentRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.paymentType() != null) predicates.add(cb.equal(root.get("paymentType"), query.paymentType()));
            if (query.direction() != null) predicates.add(cb.equal(root.get("direction"), query.direction()));
            if (query.status() != null) predicates.add(cb.equal(root.get("status"), query.status()));
            if (query.provider() != null) predicates.add(cb.equal(root.get("provider"), query.provider()));
            if (query.paymentMethod() != null) predicates.add(cb.equal(root.get("paymentMethod"), query.paymentMethod()));
            if (query.payerId() != null) predicates.add(cb.equal(root.get("payer").get("id"), query.payerId()));
            if (query.payeeId() != null) predicates.add(cb.equal(root.get("payee").get("id"), query.payeeId()));
            if (query.studentId() != null) predicates.add(cb.equal(root.get("student").get("id"), query.studentId()));
            if (query.shoppingCartId() != null) predicates.add(cb.equal(root.get("shoppingCart").get("id"), query.shoppingCartId()));

            if (query.involvedUserId() != null) {
                Join<Payment, User> payer = root.join("payer", JoinType.LEFT);
                Join<Payment, User> payee = root.join("payee", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.equal(payer.get("id"), query.involvedUserId()),
                        cb.equal(payee.get("id"), query.involvedUserId())));
            }

            if (query.academicYear() != null) predicates.add(cb.equal(root.get("academicYear"), query.academicYear()));
            if (query.term() != null) predicates.add(cb.equal(root.get("term"), query.term()));
            if (query.feeCategory() != null) predicates.add(cb.equal(root.get("feeCategory"), query.feeCategory()));

            if (query.keyword() != null) {
                String pattern = "%" + escapeLike(query.keyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("paymentReference")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("description")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("externalReference")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("receiptNumber")), pattern, ESCAPE_CHAR)));
            }

            if (query.minAmount() != null) predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), query.minAmount()));
            if (query.maxAmount() != null) predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), query.maxAmount()));
            if (query.paidFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("paidAt"), query.paidFrom()));
            if (query.paidTo() != null) predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("paidAt"), query.paidTo()));
            if (query.payPeriodFrom() != null) predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("payPeriodStart"), query.payPeriodFrom()));
            if (query.payPeriodTo() != null) predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("payPeriodEnd"), query.payPeriodTo()));

            if (Boolean.TRUE.equals(query.overdueOnly())) {
                predicates.add(cb.equal(root.get("paymentType"), PaymentType.SCHOOL_FEE));
                predicates.add(cb.lessThan(root.<LocalDate>get("dueDate"), LocalDate.now(ZoneOffset.UTC)));
                predicates.add(root.get("status").in(PaymentStatus.PENDING, PaymentStatus.REQUIRES_ACTION,
                        PaymentStatus.PROCESSING, PaymentStatus.FAILED));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}