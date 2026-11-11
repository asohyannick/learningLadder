package com.learningLadder.learningLadder.repository.partnersRepository;

import com.learningLadder.learningLadder.dto.partners.PagedQueryRequestDto;
import com.learningLadder.learningLadder.entity.partners.Partners;
import com.learningLadder.learningLadder.enums.partnerType.PartnerType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PartnersSpecification {

    private static final char ESCAPE_CHAR = '\\';

    private PartnersSpecification() {
    }

    public static Specification<Partners> fromQuery(PagedQueryRequestDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.keyword() != null) {
                String pattern = "%" + escapeLike(query.keyword().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("shortDescription")), pattern, ESCAPE_CHAR),
                        cb.like(cb.lower(root.get("industry")), pattern, ESCAPE_CHAR)
                ));
            }
            if (query.partnerType() != null) {
                predicates.add(cb.equal(root.get("partnerType"), query.partnerType()));
            }
            if (query.sponsorshipTier() != null) {
                predicates.add(cb.equal(root.get("sponsorshipTier"), query.sponsorshipTier()));
            }
            if (query.status() != null) {
                predicates.add(cb.equal(root.get("status"), query.status()));
            }
            if (query.industry() != null) {
                predicates.add(cb.equal(cb.lower(root.get("industry")), query.industry().toLowerCase(Locale.ROOT)));
            }
            if (query.country() != null) {
                predicates.add(cb.equal(cb.lower(root.get("country")), query.country().toLowerCase(Locale.ROOT)));
            }
            if (query.featured() != null) {
                predicates.add(cb.equal(root.get("featured"), query.featured()));
            }
            if (query.showOnWebsite() != null) {
                predicates.add(cb.equal(root.get("showOnWebsite"), query.showOnWebsite()));
            }
            if (Boolean.TRUE.equals(query.sponsorsOnly())) {
                predicates.add(cb.or(
                        cb.equal(root.get("partnerType"), PartnerType.SPONSOR),
                        cb.isNotNull(root.get("sponsorshipTier"))
                ));
            }
            if (query.agreementEndFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("agreementEndDate"), query.agreementEndFrom()));
            }
            if (query.agreementEndTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("agreementEndDate"), query.agreementEndTo()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}