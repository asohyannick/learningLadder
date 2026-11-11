package com.learningLadder.learningLadder.dto.partners;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.learningLadder.learningLadder.enums.partnerStatus.PartnerStatus;
import com.learningLadder.learningLadder.enums.partnerType.PartnerType;
import com.learningLadder.learningLadder.enums.sponsorshipTier.SponsorshipTier;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record PagedQueryRequestDto(

        @Size(max = 100, message = "Search keyword must not exceed 100 characters")
        String keyword,

        PartnerType partnerType,

        SponsorshipTier sponsorshipTier,

        PartnerStatus status,

        @Size(max = 100, message = "Industry must not exceed 100 characters")
        String industry,

        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        Boolean featured,

        Boolean showOnWebsite,

        Boolean sponsorsOnly,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate agreementEndFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate agreementEndTo,

        @Min(value = 0, message = "Page index must be 0 or greater")
        Integer page,

        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        Integer size,

        @Pattern(regexp = "^(displayOrder|name|createdAt|updatedAt|agreementEndDate|sponsorshipAmount)$",
                message = "sortBy must be one of: displayOrder, name, createdAt, updatedAt, agreementEndDate, sponsorshipAmount")
        String sortBy,

        @Pattern(regexp = "^(?i)(ASC|DESC)$", message = "sortDirection must be ASC or DESC")
        String sortDirection
) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final String DEFAULT_SORT_BY = "displayOrder";
    public static final String DEFAULT_SORT_DIRECTION = "ASC";

    public PagedQueryRequestDto {
        keyword = PartnerValidation.trim(keyword);
        industry = PartnerValidation.trim(industry);
        country = PartnerValidation.trim(country);
        sponsorsOnly = sponsorsOnly != null && sponsorsOnly;
        page = page == null ? DEFAULT_PAGE : page;
        size = size == null ? DEFAULT_SIZE : size;
        sortBy = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_BY : sortBy;
        sortDirection = (sortDirection == null || sortDirection.isBlank()) ? DEFAULT_SORT_DIRECTION : sortDirection;
    }

    @JsonIgnore
    @AssertTrue(message = "agreementEndFrom must be before or equal to agreementEndTo")
    public boolean isAgreementEndRangeValid() {
        return agreementEndFrom == null || agreementEndTo == null || !agreementEndFrom.isAfter(agreementEndTo);
    }

    public Pageable toPageable() {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        if (!"name".equals(sortBy)) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "name"));
        }
        return PageRequest.of(page, size, sort);
    }

    public PagedQueryRequestDto forPublicWebsite() {
        return new PagedQueryRequestDto(keyword, partnerType, sponsorshipTier, PartnerStatus.ACTIVE,
                industry, country, featured, true, sponsorsOnly, null, null,
                page, size, sortBy, sortDirection);
    }
}