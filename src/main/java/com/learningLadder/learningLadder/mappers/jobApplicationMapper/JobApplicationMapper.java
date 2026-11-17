package com.learningLadder.learningLadder.mappers.jobApplicationMapper;

import com.learningLadder.learningLadder.dto.jobApplicationDto.CreateJobApplicationRequestDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationResponseDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.UpdateJobApplicationRequestDto;
import com.learningLadder.learningLadder.entity.jobApplication.JobApplication;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface JobApplicationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "jobListing", ignore = true)
    @Mapping(target = "applicant", ignore = true)
    @Mapping(target = "coverLetterFile", ignore = true)
    @Mapping(target = "supportingDocuments", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "reviewNote", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "interviewScheduledAt", ignore = true)
    @Mapping(target = "withdrawnAt", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "profilePic", ignore = true)
    @Mapping(target = "resume", ignore = true)
    @Mapping(target = "uploadCV", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    JobApplication toEntity(CreateJobApplicationRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "jobListing", ignore = true)
    @Mapping(target = "applicant", ignore = true)
    @Mapping(target = "coverLetterFile", ignore = true)
    @Mapping(target = "supportingDocuments", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "reviewNote", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "interviewScheduledAt", ignore = true)
    @Mapping(target = "withdrawnAt", ignore = true)
    @Mapping(target = "consentGiven", ignore = true)
    @Mapping(target = "resume", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "uploadCV", ignore = true)
    @Mapping(target = "profilePic", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateJobApplicationRequestDto dto, @MappingTarget JobApplication entity);

    @Mapping(target = "jobListingId", source = "jobListing.id")
    @Mapping(target = "applicantId", source = "applicant.id")
    @Mapping(target = "supportingDocuments", source = "supportingDocuments", qualifiedByName = "fileIds")
    @Mapping(target = "supportingDocumentUrls", ignore = true)
    JobApplicationResponseDto toResponse(JobApplication entity);

    List<JobApplicationResponseDto> toResponseList(List<JobApplication> entities);

    @Named("fileIds")
    default List<String> fileIds(List<StoredFile> files) {
        return files == null ? List.of() : files.stream().map(f -> f.getId().toString()).toList();
    }
}