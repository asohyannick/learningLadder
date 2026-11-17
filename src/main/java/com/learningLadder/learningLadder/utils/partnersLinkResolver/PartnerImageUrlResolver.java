package com.learningLadder.learningLadder.utils.partnersLinkResolver;

import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Turns a stored object key (e.g. "partners/logos/abc.png") into a signed, time-limited
 * Backblaze link. Used by PartnersMapper, so every partner response gets working image links.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartnerImageUrlResolver {

    public static final Duration LINK_VALIDITY = Duration.ofDays(7);

    private final MinioFileStorageService minioFileStorageService;

    @Named("partnerImageUrl")
    public String toUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            return objectKey;
        }
        try {
            return minioFileStorageService.presignedUrl(objectKey, LINK_VALIDITY);
        } catch (Exception ex) {
            log.warn("Could not sign partner image {}: {}", objectKey, ex.getMessage());
            return null;
        }
    }
}