package com.learningLadder.learningLadder.service.jobListingService;

import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.courseDto.PagedResponseDto;
import com.learningLadder.learningLadder.dto.jobListingDto.*;
import com.learningLadder.learningLadder.entity.jobListing.JobListing;
import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import com.learningLadder.learningLadder.enums.jobStatus.JobStatus;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.mappers.jobListingMapper.JobListingMapper;
import com.learningLadder.learningLadder.repository.jobListingRepository.JobListingRepository;
import com.learningLadder.learningLadder.repository.jobListingRepository.JobListingSpecifications;
import com.learningLadder.learningLadder.utils.multipart.BufferedMultipartFile;
import com.learningLadder.learningLadder.utils.pdf.PdfTextWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobListingService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PDF_ROWS = 200;

    private final JobListingRepository jobListingRepository;
    private final JobListingMapper jobListingMapper;
    private final MinioFileStorageService storage;
    private final JobListingPhotoUploader photoUploader;

    @Transactional
    @CacheEvict(cacheNames = CacheNames.JOB_LISTINGS, allEntries = true)
    public JobListingResponseDto createJobListing(CreateJobListingRequestDto request,
                                                  MultipartFile photo,
                                                  UUID userId) {
        BufferedMultipartFile buffered = bufferPhoto(photo);

        JobListing job = jobListingMapper.toEntity(request);
        job.setSlug(uniqueSlug(request.title()));
        job.setReferenceCode(nextReferenceCode());
        job.setStatus(JobStatus.DRAFT);
        job.setPostedBy(userId);
        validateMerged(job);

        JobListing saved = jobListingRepository.save(job);
        if (buffered != null) {
            photoUploader.uploadInBackground(saved.getId(), buffered, userId);
        }
        return jobListingMapper.toResponse(saved);
    }

    @Cacheable(cacheNames = CacheNames.JOB_LISTINGS, key = "'all:' + #page + ':' + #size")
    @Transactional(readOnly = true)
    public PagedResponseDto<JobListingSummaryResponseDto> fetchAllJobListings(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PagedResponseDto.from(jobListingRepository.findAll(pageable).map(jobListingMapper::toSummary));
    }

    @Cacheable(cacheNames = CacheNames.JOB_LISTINGS, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public JobListingResponseDto fetchJobListing(UUID id) {
        return jobListingMapper.toResponse(getJob(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.JOB_LISTINGS, allEntries = true)
    public JobListingResponseDto updateJobListing(UUID id, UpdateJobListingRequestDto request, MultipartFile photo) {
        BufferedMultipartFile buffered = bufferPhoto(photo);

        JobListing job = getJob(id);
        jobListingMapper.updateEntity(request, job);
        validateMerged(job);

        if (buffered != null) {
            photoUploader.uploadInBackground(job.getId(), buffered, job.getPostedBy());
        }
        return jobListingMapper.toResponse(job);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.JOB_LISTINGS, allEntries = true)
    public void deleteJobListing(UUID id) {
        JobListing job = getJob(id);
        UUID photoFileId = job.getJobPhotoFileId();
        jobListingRepository.delete(job);

        if (photoFileId != null) {
            afterCommit(() -> {
                try { storage.delete(photoFileId); } catch (Exception ex) {
                    log.warn("Could not delete job photo [fileId={}]: {}", photoFileId, ex.getMessage());
                }
            });
        }
    }

    @Cacheable(cacheNames = CacheNames.JOB_LISTINGS, key = "'search:' + #query.cacheKey()")
    @Transactional(readOnly = true)
    public PagedResponseDto<JobListingSummaryResponseDto> searchJobListings(JobListingPagedQueryDto query) {
        Sort sort = Sort.by(Sort.Direction.fromString(query.sortDirection()), query.sortBy())
                .and(Sort.by("id"));
        Pageable pageable = PageRequest.of(query.page(), query.size(), sort);

        Page<JobListing> page = jobListingRepository.findAll(JobListingSpecifications.from(query), pageable);
        return PagedResponseDto.from(page.map(jobListingMapper::toSummary));
    }

    @Cacheable(cacheNames = CacheNames.JOB_LISTINGS, key = "'count'")
    @Transactional(readOnly = true)
    public Long countToNumberOfJobListings() {
        return jobListingRepository.count();
    }

    @Cacheable(cacheNames = CacheNames.JOB_LISTINGS, key = "'pdf:' + #query.cacheKey()")
    @Transactional(readOnly = true)
    public JobListingPdfDto downloadJobListingsPdf(JobListingPagedQueryDto query) {
        Sort sort = Sort.by(Sort.Direction.fromString(query.sortDirection()), query.sortBy())
                .and(Sort.by("id"));
        Page<JobListing> page = jobListingRepository.findAll(
                JobListingSpecifications.from(query), PageRequest.of(0, MAX_PDF_ROWS, sort));

        var rows = page.map(jobListingMapper::toSummary).getContent();
        try {
            return new JobListingPdfDto("job-listings-" + LocalDate.now() + ".pdf",
                    renderPdf(rows, page.getTotalElements()));
        } catch (IOException e) {
            throw new BadRequestException("Failed to generate job listings PDF", e);
        }
    }

    private byte[] renderPdf(java.util.List<JobListingSummaryResponseDto> rows, long total) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PdfTextWriter pdf = new PdfTextWriter(doc);

            pdf.line("Job Listings", pdf.bold, 20);
            pdf.gap(6);
            String shown = rows.size() < total ? rows.size() + " of " + total : String.valueOf(total);
            pdf.line("Generated " + LocalDate.now() + "  |  " + shown + " listing(s)", pdf.regular, 10);
            pdf.gap(12);

            int n = 1;
            for (JobListingSummaryResponseDto j : rows) {
                pdf.line(n++ + ". " + j.title(), pdf.bold, 13);
                pdf.field("Reference", j.referenceCode());
                pdf.field("Faculty", j.faculty());
                pdf.field("Department", j.department());
                pdf.field("Type", j.employmentType() == null ? null : j.employmentType().name());
                pdf.field("Work mode", j.workMode() == null ? null : j.workMode().name());
                pdf.field("Experience", j.experienceLevel() == null ? null : j.experienceLevel().name());
                pdf.field("Location", join(j.city(), j.country()));
                pdf.field("Vacancies", String.valueOf(j.numberOfVacancies()));
                pdf.field("Deadline", j.applicationDeadline() == null ? null : j.applicationDeadline().toString());
                pdf.field("Status", j.status() == null ? null : j.status().name());
                pdf.field("Summary", j.shortDescription());
                pdf.gap(12);
            }

            pdf.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }


    private JobListing getJob(UUID id) {
        return jobListingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job listing not found"));
    }

    private BufferedMultipartFile bufferPhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) return null;
        if (photo.getSize() > FileCategory.IMAGE.getMaxBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo is too large");
        }
        if (!FileCategory.IMAGE.allows(photo.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be an allowed image type");
        }
        try {
            return new BufferedMultipartFile(photo.getName(), photo.getOriginalFilename(),
                    photo.getContentType(), photo.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded photo", e);
        }
    }

    private void validateMerged(JobListing job) {
        if (job.getSalaryMin() != null && job.getSalaryMax() != null
                && job.getSalaryMin().compareTo(job.getSalaryMax()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Minimum salary cannot exceed maximum salary");
        }
        if (job.getApplicationDeadline() != null && job.getExpectedStartDate() != null
                && job.getExpectedStartDate().isBefore(job.getApplicationDeadline())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Expected start date cannot be before the application deadline");
        }
    }

    private String nextReferenceCode() {
        return "JOB-%d-%04d".formatted(LocalDate.now().getYear(), jobListingRepository.nextReferenceNumber());
    }

    private String uniqueSlug(String title) {
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String base = ascii.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "job";
        if (base.length() > 200) base = base.substring(0, 200).replaceAll("-$", "");

        String slug = base;
        int counter = 2;
        while (jobListingRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { task.run(); }
            });
        } else {
            task.run();
        }
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) return b;
        if (b == null || b.isBlank()) return a;
        return a + ", " + b;
    }
}