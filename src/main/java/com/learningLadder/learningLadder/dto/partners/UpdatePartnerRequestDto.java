package com.learningLadder.learningLadder.dto.partners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.partnerStatus.PartnerStatus;
import com.learningLadder.learningLadder.enums.partnerType.PartnerType;
import com.learningLadder.learningLadder.enums.sponsorshipTier.SponsorshipTier;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdatePartnerRequestDto(

        @Size(min = 1, max = 150, message = "Name must be between 1 and 150 characters")
        String name,

        @Size(max = 150, message = "Slug must not exceed 150 characters")
        @Pattern(regexp = PartnerValidation.SLUG_PATTERN,
                message = "Slug may only contain lowercase letters, digits and single hyphens")
        String slug,

        @Size(max = 300, message = "Short description must not exceed 300 characters")
        String shortDescription,

        @Size(max = 10_000, message = "Description must not exceed 10,000 characters")
        String description,

        @Size(max = 100, message = "Industry must not exceed 100 characters")
        String industry,

        PartnerType partnerType,

        SponsorshipTier sponsorshipTier,

        Boolean removeSponsorshipTier,

        PartnerStatus status,

        @Size(max = 5_000, message = "Benefits must not exceed 5,000 characters")
        String benefits,

        @URL(message = "Website must be a valid URL")
        @Size(max = 500, message = "Website URL must not exceed 500 characters")
        String websiteUrl,

        @URL(message = "LinkedIn URL must be a valid URL")
        @Size(max = 500, message = "LinkedIn URL must not exceed 500 characters")
        String linkedinUrl,

        @URL(message = "Facebook URL must be a valid URL")
        @Size(max = 500, message = "Facebook URL must not exceed 500 characters")
        String facebookUrl,

        @URL(message = "X / Twitter URL must be a valid URL")
        @Size(max = 500, message = "X / Twitter URL must not exceed 500 characters")
        String twitterUrl,

        @URL(message = "Instagram URL must be a valid URL")
        @Size(max = 500, message = "Instagram URL must not exceed 500 characters")
        String instagramUrl,

        @Size(max = 150, message = "Contact name must not exceed 150 characters")
        String contactPersonName,

        @Size(max = 100, message = "Contact title must not exceed 100 characters")
        String contactPersonTitle,

        @Email(message = "Contact email must be valid")
        @Size(max = 255, message = "Contact email must not exceed 255 characters")
        String contactEmail,

        @Pattern(regexp = PartnerValidation.PHONE_PATTERN, message = "Contact phone format is invalid")
        String contactPhone,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String addressLine,

        @Size(max = 100, message = "City must not exceed 100 characters")
        String city,

        @Size(max = 100, message = "State/region must not exceed 100 characters")
        String stateRegion,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        LocalDate agreementStartDate,

        LocalDate agreementEndDate,

        Boolean removeAgreementEndDate,

        UUID agreementDocumentId,

        @DecimalMin(value = "0.00", message = "Sponsorship amount cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "Sponsorship amount must have at most 12 digits and 2 decimals")
        BigDecimal sponsorshipAmount,

        @Pattern(regexp = PartnerValidation.CURRENCY_PATTERN,
                message = "Currency must be a 3-letter ISO code (e.g. USD, XAF)")
        String sponsorshipCurrency,

        Boolean showOnWebsite,

        Boolean featured,

        @Min(value = 0, message = "Display order cannot be negative")
        @Max(value = 10_000, message = "Display order must not exceed 10,000")
        Integer displayOrder,

        @Size(max = 2_000, message = "Internal notes must not exceed 2,000 characters")
        String internalNotes
) {

    public UpdatePartnerRequestDto {
        name = PartnerValidation.trim(name);
        slug = PartnerValidation.trim(slug);
        shortDescription = PartnerValidation.trim(shortDescription);
        description = PartnerValidation.trim(description);
        industry = PartnerValidation.trim(industry);
        removeSponsorshipTier = removeSponsorshipTier != null && removeSponsorshipTier;
        benefits = PartnerValidation.trim(benefits);
        websiteUrl = PartnerValidation.normalizeUrl(websiteUrl);
        linkedinUrl = PartnerValidation.normalizeUrl(linkedinUrl);
        facebookUrl = PartnerValidation.normalizeUrl(facebookUrl);
        twitterUrl = PartnerValidation.normalizeUrl(twitterUrl);
        instagramUrl = PartnerValidation.normalizeUrl(instagramUrl);
        contactPersonName = PartnerValidation.trim(contactPersonName);
        contactPersonTitle = PartnerValidation.trim(contactPersonTitle);
        contactEmail = PartnerValidation.normalizeEmail(contactEmail);
        contactPhone = PartnerValidation.trim(contactPhone);
        addressLine = PartnerValidation.trim(addressLine);
        city = PartnerValidation.trim(city);
        stateRegion = PartnerValidation.trim(stateRegion);
        country = PartnerValidation.trim(country);
        removeAgreementEndDate = removeAgreementEndDate != null && removeAgreementEndDate;
        sponsorshipCurrency = PartnerValidation.normalizeCurrency(sponsorshipCurrency);
        internalNotes = PartnerValidation.trim(internalNotes);
    }

    @JsonIgnore
    @AssertTrue(message = "Agreement end date cannot be before its start date")
    public boolean isAgreementPeriodValid() {
        return agreementStartDate == null || agreementEndDate == null || !agreementEndDate.isBefore(agreementStartDate);
    }

    @JsonIgnore
    @AssertTrue(message = "Choose either sponsorshipTier or removeSponsorshipTier, not both")
    public boolean isTierChoiceValid() {
        return !(removeSponsorshipTier && sponsorshipTier != null);
    }

    @JsonIgnore
    @AssertTrue(message = "Choose either agreementEndDate or removeAgreementEndDate, not both")
    public boolean isEndDateChoiceValid() {
        return !(removeAgreementEndDate && agreementEndDate != null);
    }
}