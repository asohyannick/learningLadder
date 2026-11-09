package com.learningLadder.learningLadder.service.jobApplicationService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.jobApplicationDto.CreateJobApplicationRequestDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationPageResponseDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationResponseDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.PagedQueryApplicationRequestDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.UpdateJobApplicationRequestDto;
import com.learningLadder.learningLadder.entity.jobApplication.JobApplication;
import com.learningLadder.learningLadder.entity.jobListing.JobListing;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.enums.applicationStatus.ApplicationStatus;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.jobApplicationMapper.JobApplicationMapper;
import com.learningLadder.learningLadder.repository.jobApplicationRepository.JobApplicationRepository;
import com.learningLadder.learningLadder.repository.jobApplicationRepository.JobApplicationSpecification;
import com.learningLadder.learningLadder.repository.jobListingRepository.JobListingRepository;
import com.learningLadder.learningLadder.repository.storedFileRepository.StoredFileRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.utils.pdf.JobApplicationPdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class JobApplicationService {

    public static final String CACHE_SINGLE = "jobApplication";
    public static final String CACHE_LIST = "jobApplications";
    public static final String CACHE_SEARCH = "jobApplicationSearch";
    public static final String CACHE_COUNT = "jobApplicationCount";
    public static final String CACHE_PDF = "jobApplicationPdf";

    private static final String DEFAULT_PROFILE_PIC = "defaults/profile-pic.png";
    private static final long MAX_PROFILE_PIC_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 2_000;

    private final JobApplicationRepository jobApplicationRepository;
    private final JobListingRepository jobListingRepository;
    private final StoredFileRepository storedFileRepository;
    private final UserRepository userRepository;
    private final JobApplicationMapper jobApplicationMapper;
    private final JobApplicationPdfGenerator jobApplicationPdfGenerator;
    private final MinioFileStorageService minioFileStorageService;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;

    private record ProfilePicUpload(String objectKey, byte[] content, String contentType) {
    }

    @CacheEvict(cacheNames = {CACHE_LIST, CACHE_SEARCH, CACHE_COUNT, CACHE_PDF}, allEntries = true)
    public JobApplicationResponseDto submitJobApplication(CreateJobApplicationRequestDto dto,
                                                          MultipartFile profilePic,
                                                          String idempotencyKey,
                                                          UUID applicantId) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("The Idempotency-Key header is required (max 100 characters)");
        }

        Optional<JobApplication> previous = jobApplicationRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            JobApplication existing = previous.get();
            if (!existing.getApplicant().getId().equals(applicantId)) {
                throw new ConflictRequestException("This Idempotency-Key has already been used");
            }
            log.info("Idempotent replay of job application {} for key {}", existing.getId(), idempotencyKey);
            return jobApplicationMapper.toResponse(existing);
        }

        if (jobApplicationRepository.existsByApplicantId(applicantId)) {
            throw new ConflictRequestException("You have already submitted a job application");
        }

        ProfilePicUpload upload = prepareProfilePic(profilePic, applicantId);

        JobApplication application = jobApplicationMapper.toEntity(dto);
        application.setApplicant(userRepository.getReferenceById(applicantId));
        application.setJobListing(findJobListing(dto.jobListingId()));
        application.setResume(findStoredFile(dto.resumeId(), "Resume"));
        if (dto.coverLetterFileId() != null) {
            application.setCoverLetterFile(findStoredFile(dto.coverLetterFileId(), "Cover letter file"));
        }
        application.setSupportingDocuments(findStoredFiles(dto.supportingDocumentIds()));
        application.setIdempotencyKey(idempotencyKey);
        application.setProfilePic(upload != null ? upload.objectKey() : DEFAULT_PROFILE_PIC);

        JobApplication saved;
        try {
            saved = jobApplicationRepository.saveAndFlush(application);
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("A job application already exists for this applicant or request");
        }

        if (upload != null) {
            uploadProfilePicInBackground(upload, null);
        }

        log.info("Job application {} submitted by applicant {}", saved.getId(), applicantId);
        return jobApplicationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_LIST, key = "#page + ':' + #size")
    public JobApplicationPageResponseDto fetchAllJobApplications(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "submittedAt"));

        return toPageResponse(jobApplicationRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_SINGLE, key = "#id")
    public JobApplicationResponseDto fetchJobApplication(UUID id) {
        return jobApplicationMapper.toResponse(findApplication(id));
    }

    @Caching(
            put = @CachePut(cacheNames = CACHE_SINGLE, key = "#id"),
            evict = @CacheEvict(cacheNames = {CACHE_LIST, CACHE_SEARCH, CACHE_PDF}, allEntries = true)
    )
    public JobApplicationResponseDto updateJobApplication(UUID id,
                                                          UpdateJobApplicationRequestDto dto,
                                                          MultipartFile profilePic,
                                                          UUID currentUserId) {
        JobApplication application = findApplication(id);

        if (!application.getApplicant().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own application");
        }
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictRequestException("This application can no longer be edited because it is " + application.getStatus());
        }

        ProfilePicUpload upload = prepareProfilePic(profilePic, currentUserId);

        jobApplicationMapper.updateEntity(dto, application);

        if (dto.resumeId() != null) {
            application.setResume(findStoredFile(dto.resumeId(), "Resume"));
        }
        if (dto.coverLetterFileId() != null) {
            application.setCoverLetterFile(findStoredFile(dto.coverLetterFileId(), "Cover letter file"));
        }
        if (dto.supportingDocumentIds() != null) {
            application.getSupportingDocuments().clear();
            application.getSupportingDocuments().addAll(findStoredFiles(dto.supportingDocumentIds()));
        }

        String previousProfilePic = null;
        if (upload != null) {
            previousProfilePic = application.getProfilePic();
            application.setProfilePic(upload.objectKey());
        }

        JobApplication saved = jobApplicationRepository.saveAndFlush(application);

        if (upload != null) {
            uploadProfilePicInBackground(upload, previousProfilePic);
        }

        log.info("Job application {} updated by applicant {}", id, currentUserId);
        return jobApplicationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_COUNT, key = "'total'")
    public long countTotalJobApplications() {
        return jobApplicationRepository.count();
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_SINGLE, key = "#id"),
            @CacheEvict(cacheNames = {CACHE_LIST, CACHE_SEARCH, CACHE_COUNT, CACHE_PDF}, allEntries = true)
    })
    public void deleteJobApplication(UUID id) {
        JobApplication application = findApplication(id);
        String profilePic = application.getProfilePic();

        jobApplicationRepository.delete(application);

        if (profilePic != null && !DEFAULT_PROFILE_PIC.equals(profilePic)) {
            fireAndForgetEmailAsync.fire("delete profile pic " + profilePic, () -> deleteFromMinio(profilePic));
        }
        log.info("Job application {} deleted", id);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_SEARCH, key = "#query")
    public JobApplicationPageResponseDto searchJobApplications(PagedQueryApplicationRequestDto query) {
        Page<JobApplication> result = jobApplicationRepository.findAll(
                JobApplicationSpecification.fromQuery(query),
                query.toPageable());

        return toPageResponse(result);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_PDF, key = "#query")
    public byte[] downloadJobApplications(PagedQueryApplicationRequestDto query) {
        Sort sort = query.toPageable().getSort();
        Page<JobApplication> result = jobApplicationRepository.findAll(
                JobApplicationSpecification.fromQuery(query),
                PageRequest.of(0, MAX_EXPORT_ROWS, sort));

        List<JobApplicationResponseDto> rows = jobApplicationMapper.toResponseList(result.getContent());

        log.info("Generating job applications PDF with {} of {} rows", rows.size(), result.getTotalElements());
        return jobApplicationPdfGenerator.generate(rows, result.getTotalElements(), describeFilters(query));
    }

    private ProfilePicUpload prepareProfilePic(MultipartFile file, UUID applicantId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_PROFILE_PIC_BYTES) {
            throw new BadRequestException("Profile picture must not exceed 5 MB");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_IMAGE_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new BadRequestException("Profile picture must be a JPEG, PNG or WEBP image");
        }

        try {
            String objectKey = "job-applications/" + applicantId + "/profile-pic-" + UUID.randomUUID() + "." + extension;
            return new ProfilePicUpload(objectKey, file.getBytes(), contentType);
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the profile picture");
        }
    }

    private void uploadProfilePicInBackground(ProfilePicUpload upload, String previousObjectKey) {
        fireAndForgetEmailAsync.fire("upload profile pic " + upload.objectKey(), () -> {
            uploadToMinio(upload);
            log.info("Profile picture uploaded to MinIO: {}", upload.objectKey());

            if (previousObjectKey != null && !DEFAULT_PROFILE_PIC.equals(previousObjectKey)) {
                try {
                    deleteFromMinio(previousObjectKey);
                } catch (Exception ex) {
                    log.warn("Could not delete old profile picture {}: {}", previousObjectKey, ex.getMessage());
                    throw  new BadRequestException("Could not delete old profile picture {}: {}");
                }
            }
        });
    }

    private void uploadToMinio(ProfilePicUpload upload) {
        try {
            minioFileStorageService.uploadFile(
                    upload.objectKey(),
                    new ByteArrayInputStream(upload.content()),
                    upload.content().length,
                    upload.contentType());
        } catch (Exception ex) {
            throw new BadRequestException("MinIO upload failed for " + upload.objectKey(), ex);
        }
    }

    private void deleteFromMinio(String objectKey) {
        try {
            minioFileStorageService.deleteFile(objectKey);
        } catch (Exception ex) {
            throw new BadRequestException("MinIO delete failed for " + objectKey, ex);
        }
    }

    private JobApplication findApplication(UUID id) {
        return jobApplicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Job application not found with id " + id));
    }

    private JobListing findJobListing(UUID id) {
        return jobListingRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Job listing not found with id " + id));
    }

    private StoredFile findStoredFile(UUID id, String label) {
        return storedFileRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException(label + " not found with id " + id));
    }

    private List<StoredFile> findStoredFiles(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<UUID> distinctIds = ids.stream().distinct().toList();
        List<StoredFile> files = storedFileRepository.findAllById(distinctIds);
        if (files.size() != distinctIds.size()) {
            throw new NotFoundRequestException("One or more supporting documents were not found");
        }
        return new ArrayList<>(files);
    }

    private JobApplicationPageResponseDto toPageResponse(Page<JobApplication> page) {
        return new JobApplicationPageResponseDto(
                jobApplicationMapper.toResponseList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private String describeFilters(PagedQueryApplicationRequestDto query) {
        List<String> parts = new ArrayList<>();
        if (query.jobListingId() != null) parts.add("job listing " + query.jobListingId());
        if (query.applicantId() != null) parts.add("applicant " + query.applicantId());
        if (query.status() != null) parts.add("status " + query.status());
        if (query.keyword() != null) parts.add("keyword \"" + query.keyword() + "\"");
        if (query.submittedFrom() != null) parts.add("from " + query.submittedFrom());
        if (query.submittedTo() != null) parts.add("to " + query.submittedTo());
        parts.add("sorted by " + query.sortBy() + " " + query.sortDirection().toUpperCase());
        return String.join("  |  ", parts);
    }
}