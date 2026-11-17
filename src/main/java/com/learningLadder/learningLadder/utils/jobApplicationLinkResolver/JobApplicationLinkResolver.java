package com.learningLadder.learningLadder.utils.jobApplicationLinkResolver;

import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationPageResponseDto;
import com.learningLadder.learningLadder.dto.jobApplicationDto.JobApplicationResponseDto;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.repository.storedFileRepository.StoredFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the storage keys in job application responses into temporary download links.
 * Runs after the cache, so every response carries links that are still valid.
 */
@Component
@RequiredArgsConstructor
public class JobApplicationLinkResolver {

    private static final Duration LINK_VALIDITY = Duration.ofHours(1);

    private final MinioFileStorageService storage;
    private final StoredFileRepository storedFileRepository;

    public JobApplicationResponseDto withLinks(JobApplicationResponseDto dto) {
        if (dto == null) {
            return null;
        }
        return withLinks(dto, loadFiles(dto.supportingDocuments()));
    }

    public JobApplicationPageResponseDto withLinks(JobApplicationPageResponseDto page) {
        List<String> allIds = page.content().stream()
                .flatMap(application -> application.supportingDocuments().stream())
                .toList();
        Map<String, StoredFile> files = loadFiles(allIds);

        List<JobApplicationResponseDto> content = page.content().stream()
                .map(application -> withLinks(application, files))
                .toList();

        return new JobApplicationPageResponseDto(content, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.first(), page.last());
    }

    private JobApplicationResponseDto withLinks(JobApplicationResponseDto dto, Map<String, StoredFile> files) {
        List<String> documentUrls = dto.supportingDocuments().stream()
                .map(files::get)
                .filter(Objects::nonNull)
                .map(storage::resolveUrl)
                .toList();

        return dto.withLinks(sign(dto.profilePic()), sign(dto.uploadCV()), documentUrls);
    }

    private String sign(String objectKey) {
        return objectKey == null || objectKey.isBlank() ? null : storage.presignedUrl(objectKey, LINK_VALIDITY);
    }

    private Map<String, StoredFile> loadFiles(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<UUID> uuids = ids.stream().distinct().map(UUID::fromString).toList();
        return storedFileRepository.findAllById(uuids).stream()
                .collect(Collectors.toMap(file -> file.getId().toString(), Function.identity()));
    }
}