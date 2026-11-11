package com.learningLadder.learningLadder.repository.partnersRepository;

import com.learningLadder.learningLadder.entity.partners.Partners;
import com.learningLadder.learningLadder.enums.partnerStatus.PartnerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PartnersRepository extends JpaRepository<Partners, UUID>, JpaSpecificationExecutor<Partners> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    Optional<Partners> findBySlugAndStatusAndShowOnWebsiteTrue(String slug, PartnerStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update versioned partners p
               set p.status = :expired
             where p.status = :active
               and p.agreementEndDate is not null
               and p.agreementEndDate < :today
            """)
    int markExpired(@Param("active") PartnerStatus active,
                    @Param("expired") PartnerStatus expired,
                    @Param("today") LocalDate today);
}