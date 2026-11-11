package com.learningLadder.learningLadder.dto.partners;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.learningLadder.learningLadder.enums.partnerType.PartnerType;
import com.learningLadder.learningLadder.enums.sponsorshipTier.SponsorshipTier;

import java.io.Serializable;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PartnerPublicResponseDto(
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
        String benefits,
        String websiteUrl,
        String linkedinUrl,
        String facebookUrl,
        String twitterUrl,
        String instagramUrl,
        String city,
        String country,
        boolean featured,
        int displayOrder
) implements Serializable {
}