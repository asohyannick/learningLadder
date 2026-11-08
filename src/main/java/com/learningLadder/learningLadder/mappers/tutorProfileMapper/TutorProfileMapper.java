package com.learningLadder.learningLadder.mappers.tutorProfileMapper;

import com.learningLadder.learningLadder.dto.tutorProfileDto.CreateTutorProfileRequestDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.TutorProfileResponseDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.TutorProfileSummaryResponseDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.UpdateTutorProfessionalRecordRequestDto;
import com.learningLadder.learningLadder.dto.tutorProfileDto.UpdateTutorProfileRequestDto;
import com.learningLadder.learningLadder.entity.tutorProfile.TutorProfile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Locale;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface TutorProfileMapper {


    @Mapping(target = "firstName", source = "firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "lastName", qualifiedByName = "trim")
    @Mapping(target = "title", source = "title", qualifiedByName = "trim")
    @Mapping(target = "currency", source = "currency", qualifiedByName = "upperTrim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "tutorNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "joinedDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TutorProfile toEntity(CreateTutorProfileRequestDto dto);

    @Mapping(target = "firstName", source = "firstName", qualifiedByName = "trim")
    @Mapping(target = "lastName", source = "lastName", qualifiedByName = "trim")
    @Mapping(target = "title", source = "title", qualifiedByName = "trim")
    @Mapping(target = "currency", source = "currency", qualifiedByName = "upperTrim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "tutorNumber", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "joinedDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateTutorProfileRequestDto dto, @MappingTarget TutorProfile profile);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "tutorNumber", source = "tutorNumber", qualifiedByName = "trim")
    @Mapping(target = "joinedDate", source = "joinedDate")
    void updateProfessionalRecord(UpdateTutorProfessionalRecordRequestDto dto, @MappingTarget TutorProfile profile);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "profilePictureUrl", source = "profilePicture.presignedUrl")
    TutorProfileResponseDto toResponse(TutorProfile profile);

    @Mapping(target = "profilePictureUrl", source = "profilePicture.presignedUrl")
    TutorProfileSummaryResponseDto toSummary(TutorProfile profile);

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }

    @Named("upperTrim")
    default String upperTrim(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}