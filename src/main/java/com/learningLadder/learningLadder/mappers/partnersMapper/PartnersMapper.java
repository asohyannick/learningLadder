package com.learningLadder.learningLadder.mappers.partnersMapper;

import com.learningLadder.learningLadder.dto.partners.CreatePartnersRequestDto;
import com.learningLadder.learningLadder.dto.partners.PartnerPublicResponseDto;
import com.learningLadder.learningLadder.dto.partners.PartnersResponseDto;
import com.learningLadder.learningLadder.dto.partners.UpdatePartnerRequestDto;
import com.learningLadder.learningLadder.entity.partners.Partners;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface PartnersMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "logo", ignore = true)
    @Mapping(target = "bannerImage", ignore = true)
    @Mapping(target = "agreementDocument", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Partners toEntity(CreatePartnersRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "logo", ignore = true)
    @Mapping(target = "bannerImage", ignore = true)
    @Mapping(target = "agreementDocument", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdatePartnerRequestDto dto, @MappingTarget Partners entity);

    @AfterMapping
    default void applyRemovals(UpdatePartnerRequestDto dto, @MappingTarget Partners entity) {
        if (Boolean.TRUE.equals(dto.removeSponsorshipTier())) {
            entity.setSponsorshipTier(null);
        }
        if (Boolean.TRUE.equals(dto.removeAgreementEndDate())) {
            entity.setAgreementEndDate(null);
        }
    }

    @Mapping(target = "agreementDocumentId", source = "agreementDocument.id")
    PartnersResponseDto toResponse(Partners entity);

    List<PartnersResponseDto> toResponseList(List<Partners> entities);

    PartnerPublicResponseDto toPublicResponse(Partners entity);

    List<PartnerPublicResponseDto> toPublicResponseList(List<Partners> entities);
}