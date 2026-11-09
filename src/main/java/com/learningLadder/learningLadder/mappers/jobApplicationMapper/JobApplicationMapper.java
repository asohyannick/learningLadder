package com.learningLadder.learningLadder.mappers.jobApplicationMapper;

import com.learningLadder.learningLadder.dto.jobApplicationDto.CreateJobApplicationRequestDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationResponseDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.UpdateJobApplicationRequestDto;
import com.learningLadder.learningLadder.entity.jobApplication.JobApplication;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface JobApplicationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "jobListing", ignore = true)
    @Mapping(target = "applicant", ignore = true)
    @Mapping(target = "resume", ignore = true)
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
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    JobApplication toEntity(CreateJobApplicationRequestDto dto);

    /* =========================================================
     * Update request DTO -> existing Entity (PATCH)
     * ---------------------------------------------------------
     * Null fields in the DTO leave the entity untouched thanks to
     * NullValuePropertyMappingStrategy.IGNORE.
     * consentGiven cannot be changed after submission.
     * ========================================================= */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "jobListing", ignore = true)
    @Mapping(target = "applicant", ignore = true)
    @Mapping(target = "resume", ignore = true)
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
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "profilePic", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateJobApplicationRequestDto dto, @MappingTarget JobApplication entity);


    @Mapping(target = "jobListingId", source = "jobListing.id")
    @Mapping(target = "applicantId", source = "applicant.id")
    @Mapping(target = "resumeId", source = "resume.id")
    @Mapping(target = "coverLetterFileId", source = "coverLetterFile.id")
    @Mapping(target = "supportingDocumentIds", source = "supportingDocuments")
    JobApplicationResponseDto toResponse(JobApplication entity);

    List<JobApplicationResponseDto> toResponseList(List<JobApplication> entities);

    default UUID storedFileToId(StoredFile file) {
        return file == null ? null : file.getId();
    }
}