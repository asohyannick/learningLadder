package com.learningLadder.learningLadder.entity.partners;

import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.enums.partnerStatus.PartnerStatus;
import com.learningLadder.learningLadder.enums.partnerType.PartnerType;
import com.learningLadder.learningLadder.enums.sponsorshipTier.SponsorshipTier;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An organization that works with Learning Ladder: academic or corporate partners,
 * government bodies, NGOs, media, and sponsors (with an optional sponsorship tier).
 * One table for both, because many organizations are partners AND sponsors.
 */
@Entity(name = "partners")
@Table(
        name = "partners",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_partners_name", columnNames = {"name"}),
                @UniqueConstraint(name = "uk_partners_slug", columnNames = {"slug"})
        },
        indexes = {
                @Index(name = "idx_partners_type", columnList = "partner_type"),
                @Index(name = "idx_partners_status", columnList = "status"),
                @Index(name = "idx_partners_tier", columnList = "sponsorship_tier"),
                @Index(name = "idx_partners_public_display", columnList = "show_on_website, featured, display_order"),
                @Index(name = "idx_partners_agreement_end", columnList = "agreement_end_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Partners {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, length = 150)
    private String slug;

    @Column(name = "logo", length = 1024)
    private String logo;

    @Column(name = "banner_image", length = 1024)
    private String bannerImage;

    @Column(name = "short_description", length = 300)
    private String shortDescription;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "industry", length = 100)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 30)
    private PartnerType partnerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "sponsorship_tier", length = 30)
    private SponsorshipTier sponsorshipTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PartnerStatus status = PartnerStatus.PENDING;

    @Column(name = "benefits", columnDefinition = "TEXT")
    private String benefits;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "facebook_url", length = 500)
    private String facebookUrl;

    @Column(name = "twitter_url", length = 500)
    private String twitterUrl;

    @Column(name = "instagram_url", length = 500)
    private String instagramUrl;

    @Column(name = "contact_person_name", length = 150)
    private String contactPersonName;

    @Column(name = "contact_person_title", length = 100)
    private String contactPersonTitle;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state_region", length = 100)
    private String stateRegion;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "agreement_start_date")
    private LocalDate agreementStartDate;

    @Column(name = "agreement_end_date")
    private LocalDate agreementEndDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreement_document_id")
    private StoredFile agreementDocument;

    @Column(name = "sponsorship_amount", precision = 14, scale = 2)
    private BigDecimal sponsorshipAmount;

    @Column(name = "sponsorship_currency", length = 3)
    private String sponsorshipCurrency = "USD";

    @Column(name = "show_on_website", nullable = false)
    private boolean showOnWebsite = true;

    @Column(name = "featured", nullable = false)
    private boolean featured = false;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "internal_notes", length = 2000)
    private String internalNotes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void validate() {
        if (agreementStartDate != null && agreementEndDate != null && agreementEndDate.isBefore(agreementStartDate)) {
            throw new BadRequestException("Agreement end date cannot be before its start date");
        }
    }

    public boolean isAgreementActiveOn(LocalDate day) {
        boolean started = agreementStartDate == null || !day.isBefore(agreementStartDate);
        boolean notEnded = agreementEndDate == null || !day.isAfter(agreementEndDate);
        return started && notEnded;
    }

    /** True for organizations that sponsor the school. */
    public boolean isSponsor() {
        return partnerType == PartnerType.SPONSOR || sponsorshipTier != null;
    }
}