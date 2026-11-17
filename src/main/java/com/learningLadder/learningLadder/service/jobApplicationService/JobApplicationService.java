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
import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.jobApplicationMapper.JobApplicationMapper;
import com.learningLadder.learningLadder.repository.jobApplicationRepository.JobApplicationRepository;
import com.learningLadder.learningLadder.repository.jobApplicationRepository.JobApplicationSpecification;
import com.learningLadder.learningLadder.repository.jobListingRepository.JobListingRepository;
import com.learningLadder.learningLadder.repository.storedFileRepository.StoredFileRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.service.userDetails.CurrentUserService;
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
import java.time.Instant;
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

    private static final long MAX_CV_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> ALLOWED_CV_TYPES = Map.of(
            "application/pdf", "pdf",
            "application/msword", "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"
    );

    private static final long MAX_SUPPORTING_DOC_BYTES = 10L * 1024 * 1024;
    private static final int MAX_SUPPORTING_DOCS = 5;
    private static final Map<String, String> ALLOWED_SUPPORTING_DOC_TYPES = Map.of(
            "application/pdf", "pdf",
            "application/msword", "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

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
    private final CurrentUserService currentUserService;

    private record PendingUpload(String objectKey, byte[] content, String contentType) {
    }

    @Transactional
    @CacheEvict(cacheNames = {CACHE_LIST, CACHE_SEARCH, CACHE_COUNT, CACHE_PDF}, allEntries = true)
    public JobApplicationResponseDto submitJobApplication(CreateJobApplicationRequestDto dto,
                                                          MultipartFile cv,
                                                          List<MultipartFile> supportingDocuments,
                                                          MultipartFile profilePic,
                                                          String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("The Idempotency-Key header is required (max 100 characters)");
        }

        UUID applicantId = currentUserService.currentUserId();

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

        PendingUpload cvUpload = prepareUpload(cv, applicantId, "cv",
                ALLOWED_CV_TYPES, MAX_CV_BYTES, true);
        PendingUpload picUpload = prepareUpload(profilePic, applicantId, "profile-pic",
                ALLOWED_IMAGE_TYPES, MAX_PROFILE_PIC_BYTES, false);
        List<MultipartFile> docFiles = nonEmpty(supportingDocuments);
        List<PendingUpload> docUploads = prepareSupportingUploads(docFiles, applicantId);

        JobApplication application = jobApplicationMapper.toEntity(dto);
        application.setApplicant(userRepository.getReferenceById(applicantId));
        application.setJobListing(findJobListing(dto.jobListingId()));
        application.setIdempotencyKey(idempotencyKey);
        application.setUploadCV(cvUpload.objectKey());
        application.setProfilePic(picUpload != null ? picUpload.objectKey() : DEFAULT_PROFILE_PIC);
        application.getSupportingDocuments().addAll(toStoredFiles(docUploads, docFiles, applicantId));

        JobApplication saved;
        try {
            saved = jobApplicationRepository.saveAndFlush(application);
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("A job application already exists for this applicant or request");
        }

        uploadInBackground(cvUpload, null);
        if (picUpload != null) {
            uploadInBackground(picUpload, null);
        }
        docUploads.forEach(u -> uploadInBackground(u, null));

        log.info("Job application {} submitted by applicant {} with {} supporting document(s)",
                saved.getId(), applicantId, docUploads.size());
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

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CACHE_COUNT, key = "'total'")
    public long countTotalJobApplications() {
        return jobApplicationRepository.count();
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

    @Transactional
    @Caching(
            put = @CachePut(cacheNames = CACHE_SINGLE, key = "#id"),
            evict = @CacheEvict(cacheNames = {CACHE_LIST, CACHE_SEARCH, CACHE_PDF}, allEntries = true)
    )
    public JobApplicationResponseDto updateJobApplication(UUID id,
                                                          UpdateJobApplicationRequestDto dto,
                                                          MultipartFile cv,
                                                          MultipartFile profilePic,
                                                          List<MultipartFile> supportingDocuments) {
        UUID currentUserId = currentUserService.currentUserId();
        JobApplication application = findApplication(id);

        if (!application.getApplicant().getId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only update your own application");
        }
        if (application.getStatus() != ApplicationStatus.SUBMITTED) {
            throw new ConflictRequestException(
                    "This application can no longer be edited because it is " + application.getStatus());
        }

        PendingUpload cvUpload = prepareUpload(cv, currentUserId, "cv",
                ALLOWED_CV_TYPES, MAX_CV_BYTES, false);
        PendingUpload picUpload = prepareUpload(profilePic, currentUserId, "profile-pic",
                ALLOWED_IMAGE_TYPES, MAX_PROFILE_PIC_BYTES, false);
        List<MultipartFile> docFiles = nonEmpty(supportingDocuments);

        jobApplicationMapper.updateEntity(dto, application);

        List<StoredFile> removedDocs = new ArrayList<>();
        if (dto.supportingDocuments() != null) {
            List<StoredFile> kept = findStoredFiles(dto.supportingDocuments(), currentUserId);
            for (StoredFile existing : application.getSupportingDocuments()) {
                if (!kept.contains(existing)) {
                    removedDocs.add(existing);
                }
            }
            application.getSupportingDocuments().clear();
            application.getSupportingDocuments().addAll(kept);
        }

        if (application.getSupportingDocuments().size() + docFiles.size() > MAX_SUPPORTING_DOCS) {
            throw new BadRequestException("You can attach at most " + MAX_SUPPORTING_DOCS + " supporting documents");
        }
        List<PendingUpload> docUploads = prepareSupportingUploads(docFiles, currentUserId);
        application.getSupportingDocuments().addAll(toStoredFiles(docUploads, docFiles, currentUserId));

        String previousCv = null;
        if (cvUpload != null) {
            previousCv = application.getUploadCV();
            application.setUploadCV(cvUpload.objectKey());
        }

        String previousPic = null;
        if (picUpload != null) {
            previousPic = application.getProfilePic();
            application.setProfilePic(picUpload.objectKey());
        }

        JobApplication saved = jobApplicationRepository.saveAndFlush(application);

        if (cvUpload != null) {
            uploadInBackground(cvUpload, previousCv);
        }
        if (picUpload != null) {
            uploadInBackground(picUpload, previousPic);
        }
        docUploads.forEach(u -> uploadInBackground(u, null));

        if (!removedDocs.isEmpty()) {
            List<String> removedKeys = removedDocs.stream().map(StoredFile::getObjectKey).toList();
            storedFileRepository.deleteAll(removedDocs);
            removedKeys.forEach(this::deleteInBackground);
        }

        log.info("Job application {} updated by applicant {}", id, currentUserId);
        return jobApplicationMapper.toResponse(saved);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_SINGLE, key = "#id"),
            @CacheEvict(cacheNames = {CACHE_LIST, CACHE_SEARCH, CACHE_COUNT, CACHE_PDF}, allEntries = true)
    })
    public void deleteJobApplication(UUID id) {
        JobApplication application = findApplication(id);
        String profilePic = application.getProfilePic();
        String uploadedCv = application.getUploadCV();
        List<StoredFile> docs = new ArrayList<>(application.getSupportingDocuments());
        List<String> docKeys = docs.stream().map(StoredFile::getObjectKey).toList();

        application.getSupportingDocuments().clear();
        jobApplicationRepository.delete(application);
        storedFileRepository.deleteAll(docs);

        deleteInBackground(profilePic);
        deleteInBackground(uploadedCv);
        docKeys.forEach(this::deleteInBackground);
        log.info("Job application {} deleted", id);
    }

    private PendingUpload prepareUpload(MultipartFile file, UUID applicantId, String folder,
                                        Map<String, String> allowedTypes, long maxBytes, boolean required) {
        if (file == null || file.isEmpty()) {
            if (required) {
                throw new BadRequestException("Please upload your " + folder.replace('-', ' ')
                        + " (" + String.join(", ", allowedTypes.values()) + ")");
            }
            return null;
        }
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File is too large (max " + maxBytes / (1024 * 1024) + " MB)");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String extension = allowedTypes.get(contentType);
        if (extension == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "File type '" + contentType + "' is not allowed. Allowed: " + allowedTypes.values());
        }

        try {
            String objectKey = "job-applications/%s/%s/%s.%s"
                    .formatted(applicantId, folder, UUID.randomUUID(), extension);
            return new PendingUpload(objectKey, file.getBytes(), contentType);
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the uploaded file", ex);
        }
    }

    private void uploadInBackground(PendingUpload upload, String previousObjectKey) {
        fireAndForgetEmailAsync.fire("upload " + upload.objectKey(), () -> {
            uploadToMinio(upload);
            log.info("File uploaded to storage: {}", upload.objectKey());

            if (previousObjectKey != null && !DEFAULT_PROFILE_PIC.equals(previousObjectKey)) {
                try {
                    deleteFromMinio(previousObjectKey);
                } catch (Exception ex) {
                    log.warn("Could not delete replaced file {}: {}", previousObjectKey, ex.getMessage());
                }
            }
        });
    }

    private void deleteInBackground(String objectKey) {
        if (objectKey == null || DEFAULT_PROFILE_PIC.equals(objectKey)) {
            return;
        }
        fireAndForgetEmailAsync.fire("delete " + objectKey, () -> {
            try {
                deleteFromMinio(objectKey);
            } catch (Exception ex) {
                log.warn("Could not delete file {}: {}", objectKey, ex.getMessage());
            }
        });
    }

    private void uploadToMinio(PendingUpload upload) {
        try {
            minioFileStorageService.uploadFile(
                    upload.objectKey(),
                    new ByteArrayInputStream(upload.content()),
                    upload.content().length,
                    upload.contentType());
        } catch (Exception ex) {
            throw new BadRequestException("Storage upload failed for " + upload.objectKey(), ex);
        }
    }

    private void deleteFromMinio(String objectKey) {
        try {
            minioFileStorageService.deleteFile(objectKey);
        } catch (Exception ex) {
            throw new BadRequestException("Storage delete failed for " + objectKey, ex);
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

    private List<StoredFile> findStoredFiles(List<String> ids, UUID ownerId) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        List<UUID> distinctIds = new ArrayList<>();
        for (String raw : ids) {
            try {
                UUID fileId = UUID.fromString(raw.trim());
                if (!distinctIds.contains(fileId)) {
                    distinctIds.add(fileId);
                }
            } catch (BadRequestException ex) {
                throw new BadRequestException("Supporting document id '" + raw + "' is not a valid UUID");
            }
        }

        List<StoredFile> files = storedFileRepository.findAllById(distinctIds);
        if (files.size() != distinctIds.size()) {
            throw new NotFoundRequestException("One or more supporting documents were not found");
        }
        boolean foreign = files.stream()
                .anyMatch(f -> !ownerId.equals(f.getUploadedBy()));
        if (foreign) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only attach supporting documents you uploaded");
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
        if (query.status() != null) parts.add("status " + query.status());
        if (query.keyword() != null) parts.add("keyword \"" + query.keyword() + "\"");
        if (query.submittedFrom() != null) parts.add("from " + query.submittedFrom());
        if (query.submittedTo() != null) parts.add("to " + query.submittedTo());
        parts.add("sorted by " + query.sortBy() + " " + query.sortDirection().toUpperCase());
        return String.join("  |  ", parts);
    }

    private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        if (files == null) return List.of();
        return files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    private List<PendingUpload> prepareSupportingUploads(List<MultipartFile> files, UUID ownerId) {
        if (files.size() > MAX_SUPPORTING_DOCS) {
            throw new BadRequestException("You can attach at most " + MAX_SUPPORTING_DOCS + " supporting documents");
        }
        List<PendingUpload> uploads = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            uploads.add(prepareUpload(file, ownerId, "supporting-document",
                    ALLOWED_SUPPORTING_DOC_TYPES, MAX_SUPPORTING_DOC_BYTES, true));
        }
        return uploads;
    }

    private List<StoredFile> toStoredFiles(List<PendingUpload> uploads, List<MultipartFile> files, UUID ownerId) {
        List<StoredFile> stored = new ArrayList<>(uploads.size());
        for (int i = 0; i < uploads.size(); i++) {
            MultipartFile file = files.get(i);
            PendingUpload upload = uploads.get(i);
            StoredFile sf = StoredFile.builder()
                    .objectKey(upload.objectKey())
                    .originalFileName(file.getOriginalFilename())
                    .contentType(upload.contentType())
                    .sizeBytes(file.getSize())
                    .category(FileCategory.SUPPORTING_DOCUMENT)
                    .presignedUrl("")
                    .presignedUrlExpiresAt(Instant.EPOCH)
                    .uploadedBy(ownerId)
                    .build();
            stored.add(storedFileRepository.save(sf));
        }
        return stored;
    }
}