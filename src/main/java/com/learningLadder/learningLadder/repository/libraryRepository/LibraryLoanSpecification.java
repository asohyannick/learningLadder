package com.learningLadder.learningLadder.repository.libraryRepository;

import com.learningLadder.learningLadder.dto.libraryLoanDto.PagedQueryLibraryLoanRequestDto;
import com.learningLadder.learningLadder.entity.library.Library;
import com.learningLadder.learningLadder.entity.library.LibraryLoan;
import com.learningLadder.learningLadder.enums.loanStatus.LoanStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LibraryLoanSpecification {

    private static final char ESCAPE_CHAR = '\\';

    private LibraryLoanSpecification() {
    }

    public static Specification<LibraryLoan> fromQuery(PagedQueryLibraryLoanRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.bookId() != null) {
                predicates.add(cb.equal(root.get("book").get("id"), query.bookId()));
            }
            if (query.studentId() != null) {
                predicates.add(cb.equal(root.get("student").get("id"), query.studentId()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (Boolean.TRUE.equals(query.overdueOnly())) {
                predicates.add(root.get("status").in(LoanStatus.BORROWED, LoanStatus.OVERDUE));
                predicates.add(cb.lessThan(root.<Instant>get("dueAt"), Instant.now()));
            }
            if (Boolean.TRUE.equals(query.unpaidFeesOnly())) {
                predicates.add(cb.isFalse(root.get("feePaid")));
                predicates.add(cb.greaterThan(root.<BigDecimal>get("lateFee"), BigDecimal.ZERO));
            }
            if (query.dueFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("dueAt"), query.dueFrom()));
            }
            if (query.dueTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("dueAt"), query.dueTo()));
            }
            if (query.keyword() != null) {
                String pattern = "%" + escapeLike(query.keyword().toLowerCase(Locale.ROOT)) + "%";
                Join<LibraryLoan, Library> book = root.join("book");
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("loanReference")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(book.get("title")), pattern, ESCAPE_CHAR)
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}