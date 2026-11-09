package com.learningLadder.learningLadder.mappers.jobListingMapper;

import com.learningLadder.learningLadder.dto.jobListingDto.CreateJobListingRequestDto;
import com.learningLadder.learningLadder.dto.jobListingDto.JobListingResponseDto;
import com.learningLadder.learningLadder.dto.jobListingDto.JobListingSummaryResponseDto;
import com.learningLadder.learningLadder.dto.jobListingDto.UpdateJobListingRequestDto;
import com.learningLadder.learningLadder.entity.jobListing.JobListing;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface JobListingMapper {

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "title", source = "title", qualifiedByName = "trim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "referenceCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "postedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "jobPhotoFileId", ignore = true)
    JobListing toEntity(CreateJobListingRequestDto dto);

    @BeanMapping(nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
    @Mapping(target = "title", source = "title", qualifiedByName = "trim")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "referenceCode", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "publishedAt", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "postedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateJobListingRequestDto dto, @MappingTarget JobListing jobListing);

    JobListingResponseDto toResponse(JobListing jobListing);

    JobListingSummaryResponseDto toSummary(JobListing jobListing);

    @Named("trim")
    default String trim(String value) {
        return value == null ? null : value.trim();
    }
}