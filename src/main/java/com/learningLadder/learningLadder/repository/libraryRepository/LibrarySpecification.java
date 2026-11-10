package com.learningLadder.learningLadder.repository.libraryRepository;

import com.learningLadder.learningLadder.dto.libraryLoanDto.PagedQueryLibraryBookRequestDto;
import com.learningLadder.learningLadder.entity.library.Library;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LibrarySpecification {

    private static final char ESCAPE_CHAR = '\\';

    private LibrarySpecification() {
    }

    public static Specification<Library> fromQuery(PagedQueryLibraryBookRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.keyword() != null) {
                String keyword = query.keyword().toLowerCase(Locale.ROOT);
                String pattern = "%" + escapeLike(keyword) + "%";
                String isbnPattern = "%" + escapeLike(keyword.replaceAll("[\\s-]", "")) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("author")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("isbn")), isbnPattern, ESCAPE_CHAR)
                ));
            }
            if (query.category() != null) {
                predicates.add(cb.equal(cb.lower(root.get("category")), query.category().toLowerCase(Locale.ROOT)));
            }
            if (query.language() != null) {
                predicates.add(cb.equal(cb.lower(root.get("language")), query.language().toLowerCase(Locale.ROOT)));
            }
            if (query.courseId() != null) {
                predicates.add(cb.equal(root.get("course").get("id"), query.courseId()));
            }
            if (query.active() != null) {
                predicates.add(cb.equal(root.get("active"), query.active()));
            }
            if (Boolean.TRUE.equals(query.availableOnly())) {
                predicates.add(cb.isTrue(root.get("active")));
                predicates.add(cb.isTrue(root.get("borrowable")));
                predicates.add(cb.greaterThan(root.get("availableCopies"), 0));
            }
            if (query.publicationYearFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("publicationYear"), query.publicationYearFrom()));
            }
            if (query.publicationYearTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("publicationYear"), query.publicationYearTo()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}