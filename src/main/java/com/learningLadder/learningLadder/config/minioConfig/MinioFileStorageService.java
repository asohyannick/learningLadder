package com.learningLadder.learningLadder.config.minioConfig;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import com.learningLadder.learningLadder.exception.fileStorageException.FileStorageException;
import com.learningLadder.learningLadder.repository.storedFileRepository.StoredFileRepository;
import io.minio.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class MinioFileStorageService {

    private static final int PRESIGNED_EXPIRY_SECONDS = (int) Duration.ofDays(7).toSeconds();

    private record SignedUrl(String url, Instant expiresAt) { }

    private final MinioClient minioClient;
    private final MinioClient publicClient;
    private final MinioConfig config;
    private final StoredFileRepository repository;

    public MinioFileStorageService(
            MinioClient minioClient,
            @Qualifier("minioPublicClient")
            MinioClient publicClient,
            MinioConfig config,
            StoredFileRepository repository
    ) {
        this.minioClient = minioClient;
        this.publicClient = publicClient;
        this.config = config;
        this.repository = repository;
    }

    // =====================================================================
    // Upload: file goes to MinIO, only metadata + presigned URL go to Postgres
    // =====================================================================
    public StoredFile upload(MultipartFile file, FileCategory category, UUID uploadedBy) {
        validate(file, category);
        String objectKey = buildObjectKey(file, category);

        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(config.getBucketName())
                    .object(objectKey)
                    .stream(in, file.getSize(), -1L)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception ex) {
            throw new FileStorageException("Failed to upload file", ex);
        }

        try {
            SignedUrl signed = sign(objectKey);
            return repository.save(StoredFile.builder()
                    .objectKey(objectKey)
                    .originalFileName(safeFileName(file.getOriginalFilename()))
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .category(category)
                    .presignedUrl(signed.url())
                    .presignedUrlExpiresAt(signed.expiresAt())
                    .uploadedBy(uploadedBy)
                    .build());
        } catch (RuntimeException ex) {
            removeObjectQuietly(objectKey);
            throw ex;
        }
    }

    // =====================================================================
    // Fetch: returns a URL that is still valid, regenerating it when needed
    // =====================================================================
    /**
     * Call this whenever you return a file to the frontend. If the stored URL is expired, or has
     * less than the configured threshold left, a new 7-day URL is generated and saved.
     *
     * REQUIRES_NEW so the refresh is really written even when the caller is a
     * @Transactional(readOnly = true) read method (writes inside a read-only transaction are silently lost).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String resolveUrl(StoredFile file) {
        Instant refreshWhenBefore = Instant.now()
                .plus(Duration.ofHours(config.getPresignedRefreshThresholdHours()));

        if (file.getPresignedUrl() != null
                && file.getPresignedUrlExpiresAt() != null
                && file.getPresignedUrlExpiresAt().isAfter(refreshWhenBefore)) {
            return file.getPresignedUrl();
        }

        SignedUrl signed = sign(file.getObjectKey());
        repository.updatePresignedUrl(file.getId(), signed.url(), signed.expiresAt(), Instant.now());

        file.setPresignedUrl(signed.url());
        file.setPresignedUrlExpiresAt(signed.expiresAt());
        log.debug("Presigned URL refreshed [fileId={}]", file.getId());
        return signed.url();
    }

    // =====================================================================
    // Delete
    // =====================================================================
    public void delete(UUID fileId) {
        StoredFile file = repository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException("File not found: " + fileId));
        repository.delete(file);
        removeObjectQuietly(file.getObjectKey());
    }


    /**
     * Uploads raw bytes to MinIO under the given object key.
     * Unlike upload(...), it does not create a StoredFile row, so the caller keeps the key itself.
     * Safe to call from a background thread because it takes a stream, not a MultipartFile.
     */
    public void uploadFile(String objectKey, InputStream content, long size, String contentType) {
        if (!StringUtils.hasText(objectKey)) {
            throw new FileStorageException("Object key must not be empty");
        }
        try (InputStream in = content) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(config.getBucketName())
                    .object(objectKey)
                    .stream(in, size, -1L)
                    .contentType(contentType)
                    .build());
            log.debug("Object uploaded to MinIO [{}] ({} bytes)", objectKey, size);
        } catch (Exception ex) {
            throw new FileStorageException("Failed to upload object " + objectKey, ex);
        }
    }

    /**
     * Deletes a raw object from MinIO by its key.
     * Unlike removeObjectQuietly(...), it throws on failure so the background job can retry.
     * Deleting a key that does not exist is not an error in MinIO.
     */
    public void deleteFile(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(config.getBucketName())
                    .object(objectKey)
                    .build());
            log.debug("Object removed from MinIO [{}]", objectKey);
        } catch (Exception ex) {
            throw new FileStorageException("Failed to delete object " + objectKey, ex);
        }
    }

    // =====================================================================
    // Internals
    // =====================================================================
    private SignedUrl sign(String objectKey) {
        Instant issuedAt = Instant.now();
        try {
            String url = publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Http.Method.GET)
                    .bucket(config.getBucketName())
                    .object(objectKey)
                    .expiry(PRESIGNED_EXPIRY_SECONDS)
                    .build());
            return new SignedUrl(url, issuedAt.plusSeconds(PRESIGNED_EXPIRY_SECONDS));
        } catch (Exception ex) {
            throw new FileStorageException("Failed to generate presigned URL", ex);
        }
    }


    private void validate(MultipartFile file, FileCategory category) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The " + category.name().toLowerCase(Locale.ROOT) + " file is empty");
        }
        if (file.getSize() > category.getMaxBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File too large. The maximum for " + category.name().toLowerCase(Locale.ROOT) + " files is "
                            + (category.getMaxBytes() / (1024 * 1024)) + " MB");
        }
        if (!category.allows(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "'" + safeFileName(file.getOriginalFilename()) + "' (" + file.getContentType()
                            + ") is not allowed here: this field expects a "
                            + category.name().toLowerCase(Locale.ROOT) + " file");
        }
    }

    private String buildObjectKey(MultipartFile file, FileCategory category) {
        LocalDate today = LocalDate.now();
        String extension = StringUtils.getFilenameExtension(safeFileName(file.getOriginalFilename()));
        String suffix = "";
        if (extension != null) {
            String clean = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!clean.isEmpty() && clean.length() <= 10) suffix = "." + clean;
        }
        return "%s/%d/%02d/%s%s".formatted(category.getFolder(), today.getYear(),
                today.getMonthValue(), UUID.randomUUID(), suffix);
    }

    private String safeFileName(String original) {
        if (original == null) return "file";
        String name = StringUtils.getFilename(StringUtils.cleanPath(original));
        if (name == null || name.isBlank()) return "file";
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private void removeObjectQuietly(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(config.getBucketName())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            log.warn("Could not remove MinIO object [{}]: {}", objectKey, ex.getMessage());
        }
    }
}