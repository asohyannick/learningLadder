package com.learningLadder.learningLadder.service.jobListingService;

import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.entity.jobListing.JobListing;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import com.learningLadder.learningLadder.repository.jobListingRepository.JobListingRepository;
import com.learningLadder.learningLadder.utils.multipart.BufferedMultipartFile;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class JobListingPhotoUploader {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BACKOFF_MS = 2_000;

    private record Attached(boolean attached, UUID oldFileId) { }

    private final ThreadPoolExecutor executor;
    private final MinioFileStorageService storage;
    private final JobListingRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;

    public JobListingPhotoUploader(
            MinioFileStorageService storage,
            JobListingRepository repository,
            TransactionTemplate transactionTemplate,
            CacheManager cacheManager,
            @Value("${app.uploads.async.pool-size:2}") int poolSize,
            @Value("${app.uploads.async.queue-capacity:200}") int queueCapacity) {

        this.storage = storage;
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.cacheManager = cacheManager;

        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                poolSize, poolSize, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> new Thread(r, "job-photo-upload-" + counter.incrementAndGet()),
                new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    /** Queues the upload to start after the surrounding transaction commits. */
    public void uploadInBackground(UUID jobId, BufferedMultipartFile photo, UUID userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(jobId, photo, userId);
                }
            });
        } else {
            submit(jobId, photo, userId);
        }
    }

    private void submit(UUID jobId, BufferedMultipartFile photo, UUID userId) {
        try {
            executor.execute(() -> process(jobId, photo, userId));
        } catch (RejectedExecutionException ex) {
            log.error("Photo upload queue is full, photo dropped [jobId={}]", jobId);
        }
    }

    private void process(UUID jobId, BufferedMultipartFile photo, UUID userId) {
        StoredFile stored = uploadWithRetry(jobId, photo, userId);
        if (stored == null) return;

        try {
            Attached result = transactionTemplate.execute(status -> {
                JobListing job = repository.findById(jobId).orElse(null);
                if (job == null) return new Attached(false, null);   // deleted while uploading
                UUID oldFileId = job.getJobPhotoFileId();
                job.setJobPhotoFileId(stored.getId());
                job.setJobPhotoUrl(stored.getPresignedUrl());
                return new Attached(true, oldFileId);
            });

            if (result == null || !result.attached()) {
                deleteQuietly(stored.getId());
                return;
            }
            evictCache();
            if (result.oldFileId() != null) deleteQuietly(result.oldFileId());
            log.debug("Job photo attached [jobId={}, fileId={}]", jobId, stored.getId());

        } catch (RuntimeException ex) {
            log.error("Could not attach job photo [jobId={}]: {}", jobId, ex.getMessage());
            deleteQuietly(stored.getId());
        }
    }

    private StoredFile uploadWithRetry(UUID jobId, BufferedMultipartFile photo, UUID userId) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return storage.upload(photo, FileCategory.IMAGE, userId);
            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Job photo upload failed permanently [jobId={}]: {}", jobId, ex.getMessage());
                    return null;
                }
                log.warn("Job photo upload attempt {}/{} failed [jobId={}]: {}",
                        attempt, MAX_ATTEMPTS, jobId, ex.getMessage());
                try {
                    Thread.sleep(BACKOFF_MS * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    private void evictCache() {
        Cache cache = cacheManager.getCache(CacheNames.JOB_LISTINGS);
        if (cache != null) cache.clear();
    }

    private void deleteQuietly(UUID fileId) {
        try { storage.delete(fileId); } catch (Exception ignored) { }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}