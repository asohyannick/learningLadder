package com.learningLadder.learningLadder.dto.partners;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.partnerStatus.PartnerStatus;
import com.learningLadder.learningLadder.enums.partnerType.PartnerType;
import com.learningLadder.learningLadder.enums.sponsorshipTier.SponsorshipTier;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartnersResponseDto(

        UUID id,

        String name,
        String slug,
        String logo,
        String bannerImage,
        String shortDescription,
        String description,
        String industry,

        PartnerType partnerType,
        SponsorshipTier sponsorshipTier,
        PartnerStatus status,
        String benefits,

        String websiteUrl,
        String linkedinUrl,
        String facebookUrl,
        String twitterUrl,
        String instagramUrl,

        String contactPersonName,
        String contactPersonTitle,
        String contactEmail,
        String contactPhone,

        String addressLine,
        String city,
        String stateRegion,
        String country,

        LocalDate agreementStartDate,
        LocalDate agreementEndDate,
        UUID agreementDocumentId,
        BigDecimal sponsorshipAmount,
        String sponsorshipCurrency,

        boolean showOnWebsite,
        boolean featured,
        int displayOrder,

        String internalNotes,
        UUID createdBy,

        Instant createdAt,
        Instant updatedAt
) implements Serializable {

    public boolean sponsor() {
        return partnerType == PartnerType.SPONSOR || sponsorshipTier != null;
    }

    public boolean agreementActive() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        boolean started = agreementStartDate == null || !today.isBefore(agreementStartDate);
        boolean notEnded = agreementEndDate == null || !today.isAfter(agreementEndDate);
        return started && notEnded;
    }

    public Long daysUntilAgreementEnds() {
        return agreementEndDate == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(ZoneOffset.UTC), agreementEndDate);
    }
}