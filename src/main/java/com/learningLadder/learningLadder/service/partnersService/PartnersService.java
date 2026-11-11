package com.learningLadder.learningLadder.service.partnersService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.partners.CreatePartnersRequestDto;
import com.learningLadder.learningLadder.dto.partners.PagedQueryRequestDto;
import com.learningLadder.learningLadder.dto.partners.PartnerPublicPageResponseDto;
import com.learningLadder.learningLadder.dto.partners.PartnerPublicResponseDto;
import com.learningLadder.learningLadder.dto.partners.PartnersPageResponseDto;
import com.learningLadder.learningLadder.dto.partners.PartnersResponseDto;
import com.learningLadder.learningLadder.dto.partners.UpdatePartnerRequestDto;
import com.learningLadder.learningLadder.entity.partners.Partners;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.enums.partnerStatus.PartnerStatus;
import com.learningLadder.learningLadder.enums.partnerType.PartnerType;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.fileStorageException.FileStorageException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.partnersMapper.PartnersMapper;
import com.learningLadder.learningLadder.repository.partnersRepository.PartnersRepository;
import com.learningLadder.learningLadder.repository.partnersRepository.PartnersSpecification;
import com.learningLadder.learningLadder.repository.storedFileRepository.StoredFileRepository;
import com.learningLadder.learningLadder.utils.image.ImageOptimizer;
import com.learningLadder.learningLadder.utils.image.ImageOptimizer.PreparedImage;
import com.learningLadder.learningLadder.utils.pdf.PartnersPdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PartnersService {

    /** Clears every partner cache after a successful write. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @CacheEvict(cacheNames = {
            CacheNames.PARTNER, CacheNames.PARTNERS, CacheNames.PARTNER_SEARCH,
            CacheNames.PARTNER_COUNT, CacheNames.PARTNER_PDF, CacheNames.PARTNER_PUBLIC
    }, allEntries = true)
    public @interface EvictPartnerCaches {
    }

    private static final List<String> ALL_PARTNER_CACHES = List.of(
            CacheNames.PARTNER, CacheNames.PARTNERS, CacheNames.PARTNER_SEARCH,
            CacheNames.PARTNER_COUNT, CacheNames.PARTNER_PDF, CacheNames.PARTNER_PUBLIC);

    private static final String LOGO_FOLDER = "partners/logos";
    private static final String BANNER_FOLDER = "partners/banners";
    private static final int LOGO_MAX_DIMENSION = 512;
    private static final int BANNER_MAX_DIMENSION = 1920;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 2_000;

    private final PartnersMapper partnersMapper;
    private final PartnersRepository partnersRepository;
    private final StoredFileRepository storedFileRepository;
    private final MinioFileStorageService minioFileStorageService;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;
    private final ImageOptimizer imageOptimizer;
    private final PartnersPdfGenerator partnersPdfGenerator;
    private final CacheManager cacheManager;

    @EvictPartnerCaches
    public PartnersResponseDto addPartnership(CreatePartnersRequestDto dto, MultipartFile logo,
                                              MultipartFile bannerImage, UUID adminId) {
        if (partnersRepository.existsByNameIgnoreCase(dto.name())) {
            throw new ConflictRequestException("A partner named \"" + dto.name() + "\" already exists");
        }
        if (partnersRepository.existsBySlug(dto.slug())) {
            throw new ConflictRequestException("The slug \"" + dto.slug() + "\" is already used by another partner");
        }

        PreparedImage logoImage = imageOptimizer.prepare(logo, LOGO_FOLDER, LOGO_MAX_DIMENSION, "Logo");
        PreparedImage bannerUpload = imageOptimizer.prepare(bannerImage, BANNER_FOLDER, BANNER_MAX_DIMENSION, "Banner image");

        Partners partner = partnersMapper.toEntity(dto);
        if (dto.agreementDocumentId() != null) {
            partner.setAgreementDocument(findStoredFile(dto.agreementDocumentId()));
        }
        partner.setCreatedBy(adminId);
        partner.setLogo(logoImage == null ? null : logoImage.objectKey());
        partner.setBannerImage(bannerUpload == null ? null : bannerUpload.objectKey());

        Partners saved;
        try {
            saved = partnersRepository.saveAndFlush(partner);
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("A partner with this name or slug already exists");
        }

        uploadImageInBackground(logoImage, null);
        uploadImageInBackground(bannerUpload, null);

        log.info("Partner {} \"{}\" ({}) added by {}", saved.getId(), saved.getName(), saved.getPartnerType(), adminId);
        return partnersMapper.toResponse(saved);
    }

    @EvictPartnerCaches
    public PartnersResponseDto updatePartnership(UUID id, UpdatePartnerRequestDto dto,
                                                 MultipartFile logo, MultipartFile bannerImage) {
        Partners partner = findPartner(id);

        if (dto.name() != null && partnersRepository.existsByNameIgnoreCaseAndIdNot(dto.name(), id)) {
            throw new ConflictRequestException("Another partner is already named \"" + dto.name() + "\"");
        }
        if (dto.slug() != null && partnersRepository.existsBySlugAndIdNot(dto.slug(), id)) {
            throw new ConflictRequestException("The slug \"" + dto.slug() + "\" is already used by another partner");
        }

        PreparedImage logoImage = imageOptimizer.prepare(logo, LOGO_FOLDER, LOGO_MAX_DIMENSION, "Logo");
        PreparedImage bannerUpload = imageOptimizer.prepare(bannerImage, BANNER_FOLDER, BANNER_MAX_DIMENSION, "Banner image");

        partnersMapper.updateEntity(dto, partner);

        if (dto.agreementDocumentId() != null) {
            partner.setAgreementDocument(findStoredFile(dto.agreementDocumentId()));
        }

        validateMergedPartner(partner);

        String previousLogo = null;
        if (logoImage != null) {
            previousLogo = partner.getLogo();
            partner.setLogo(logoImage.objectKey());
        }
        String previousBanner = null;
        if (bannerUpload != null) {
            previousBanner = partner.getBannerImage();
            partner.setBannerImage(bannerUpload.objectKey());
        }

        Partners saved;
        try {
            saved = partnersRepository.saveAndFlush(partner);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictRequestException("A partner with this name or slug already exists");
        } catch (ConflictRequestException ex) {
            throw new ConflictRequestException("This partner was changed by someone else. Please reload and try again.");
        }

        uploadImageInBackground(logoImage, previousLogo);
        uploadImageInBackground(bannerUpload, previousBanner);

        log.info("Partner {} updated", id);
        return partnersMapper.toResponse(saved);
    }

    @EvictPartnerCaches
    public void deletePartnership(UUID id) {
        Partners partner = findPartner(id);
        String logo = partner.getLogo();
        String banner = partner.getBannerImage();

        partnersRepository.delete(partner);

        deleteImageInBackground(logo);
        deleteImageInBackground(banner);
        log.info("Partner {} \"{}\" deleted", id, partner.getName());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNERS, key = "#page + ':' + #size")
    public PartnersPageResponseDto fetchAllPartnerships(int page, int size) {
        Pageable pageable = PageRequest.of(safePage(page), safeSize(size),
                Sort.by(Sort.Direction.DESC, "featured")
                        .and(Sort.by(Sort.Direction.ASC, "displayOrder"))
                        .and(Sort.by(Sort.Direction.ASC, "name")));
        return toPage(partnersRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNER, key = "#id")
    public PartnersResponseDto fetchPartnership(UUID id) {
        return partnersMapper.toResponse(findPartner(id));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNER_SEARCH, key = "#query")
    public PartnersPageResponseDto searchPartnerships(PagedQueryRequestDto query) {
        return toPage(partnersRepository.findAll(PartnersSpecification.fromQuery(query), query.toPageable()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNER_COUNT, key = "'total'")
    public long countTotalNumberOfPartnerships() {
        return partnersRepository.count();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNER_PDF, key = "#query")
    public byte[] downloadPartnerships(PagedQueryRequestDto query) {
        Sort sort = query.toPageable().getSort();
        Page<Partners> result = partnersRepository.findAll(
                PartnersSpecification.fromQuery(query), PageRequest.of(0, MAX_EXPORT_ROWS, sort));

        List<PartnersResponseDto> rows = partnersMapper.toResponseList(result.getContent());

        log.info("Generating partners PDF with {} of {} rows", rows.size(), result.getTotalElements());
        return partnersPdfGenerator.generate(rows, result.getTotalElements(), describeFilters(query));
    }


    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNER_PUBLIC, key = "'list:' + #page + ':' + #size")
    public PartnerPublicPageResponseDto fetchPublicPartnerships(int page, int size) {
        PagedQueryRequestDto query = new PagedQueryRequestDto(null, null, null, null, null, null, null, null,
                null, null, null, safePage(page), safeSize(size), null, null).forPublicWebsite();
        Pageable pageable = PageRequest.of(query.page(), query.size(),
                Sort.by(Sort.Direction.DESC, "featured")
                        .and(Sort.by(Sort.Direction.ASC, "displayOrder"))
                        .and(Sort.by(Sort.Direction.ASC, "name")));
        return toPublicPage(partnersRepository.findAll(PartnersSpecification.fromQuery(query), pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNER_PUBLIC, key = "'search:' + #query")
    public PartnerPublicPageResponseDto searchPublicPartnerships(PagedQueryRequestDto query) {
        PagedQueryRequestDto publicQuery = query.forPublicWebsite();
        return toPublicPage(partnersRepository.findAll(
                PartnersSpecification.fromQuery(publicQuery), publicQuery.toPageable()));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.PARTNER_PUBLIC, key = "'slug:' + #slug")
    public PartnerPublicResponseDto fetchPublicPartnershipBySlug(String slug) {
        return partnersRepository.findBySlugAndStatusAndShowOnWebsiteTrue(slug, PartnerStatus.ACTIVE)
                .map(partnersMapper::toPublicResponse)
                .orElseThrow(() -> new NotFoundRequestException("Partner not found: " + slug));
    }

    // #####################################################################
    //                          SCHEDULED JOB
    // #####################################################################

    /** Every day at 00:30 (server time): ACTIVE partners whose agreement ended become EXPIRED. */
    @Scheduled(cron = "0 30 0 * * *")
    public void markExpiredPartnerships() {
        int expired = partnersRepository.markExpired(PartnerStatus.ACTIVE, PartnerStatus.EXPIRED,
                LocalDate.now(ZoneOffset.UTC));
        if (expired > 0) {
            log.info("Marked {} partnership(s) as EXPIRED", expired);
            ALL_PARTNER_CACHES.forEach(name -> {
                Cache cache = cacheManager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
    }

    // #####################################################################
    //                             HELPERS
    // #####################################################################

    /** Business rules that depend on the final state after a PATCH. */
    private void validateMergedPartner(Partners partner) {
        if (partner.getAgreementStartDate() != null && partner.getAgreementEndDate() != null
                && partner.getAgreementEndDate().isBefore(partner.getAgreementStartDate())) {
            throw new BadRequestException("Agreement end date (" + partner.getAgreementEndDate()
                    + ") cannot be before its start date (" + partner.getAgreementStartDate() + ")");
        }
        if (partner.getPartnerType() == PartnerType.SPONSOR && partner.getSponsorshipTier() == null) {
            throw new BadRequestException("A sponsorship tier is required for sponsors");
        }
    }

    /**
     * Resizes (JPEG/PNG) and uploads an image after the transaction commits, on the background
     * executor with retries. A failure is only logged: the partner is already saved.
     */
    private void uploadImageInBackground(PreparedImage image, String previousObjectKey) {
        if (image == null) {
            return;
        }
        fireAndForgetEmailAsync.fire("upload partner image " + image.objectKey(), () -> {
            byte[] content = imageOptimizer.optimize(image);
            try {
                minioFileStorageService.uploadFile(image.objectKey(), new ByteArrayInputStream(content),
                        content.length, image.contentType());
            } catch (Exception ex) {
                throw new FileStorageException("MinIO upload failed for " + image.objectKey(), ex);
            }
            log.info("Partner image uploaded: {} ({} bytes)", image.objectKey(), content.length);

            if (previousObjectKey != null) {
                try {
                    minioFileStorageService.deleteFile(previousObjectKey);
                } catch (Exception ex) {
                    log.warn("Could not delete old partner image {}: {}", previousObjectKey, ex.getMessage());
                    throw new BadRequestException("Could not delete old partner image", ex.getCause());
                }
            }
        });
    }

    private void deleteImageInBackground(String objectKey) {
        if (objectKey == null) {
            return;
        }
        fireAndForgetEmailAsync.fire("delete partner image " + objectKey, () -> {
            try {
                minioFileStorageService.deleteFile(objectKey);
            } catch (Exception ex) {
                throw new FileStorageException("MinIO delete failed for " + objectKey, ex);
            }
        });
    }

    private Partners findPartner(UUID id) {
        return partnersRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Partner not found with id " + id));
    }

    private StoredFile findStoredFile(UUID id) {
        return storedFileRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Agreement document not found with id " + id));
    }

    private int safePage(int page) {
        return Math.max(page, 0);
    }

    private int safeSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private PartnersPageResponseDto toPage(Page<Partners> page) {
        return new PartnersPageResponseDto(partnersMapper.toResponseList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private PartnerPublicPageResponseDto toPublicPage(Page<Partners> page) {
        return new PartnerPublicPageResponseDto(partnersMapper.toPublicResponseList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private String describeFilters(PagedQueryRequestDto query) {
        List<String> parts = new ArrayList<>();
        if (query.keyword() != null) parts.add("keyword \"" + query.keyword() + "\"");
        if (query.partnerType() != null) parts.add("type " + query.partnerType());
        if (query.sponsorshipTier() != null) parts.add("tier " + query.sponsorshipTier());
        if (query.status() != null) parts.add("status " + query.status());
        if (query.industry() != null) parts.add("industry " + query.industry());
        if (query.country() != null) parts.add("country " + query.country());
        if (query.featured() != null) parts.add(query.featured() ? "featured only" : "not featured");
        if (query.showOnWebsite() != null) parts.add(query.showOnWebsite() ? "on website" : "hidden from website");
        if (Boolean.TRUE.equals(query.sponsorsOnly())) parts.add("sponsors only");
        if (query.agreementEndFrom() != null) parts.add("agreement ends from " + query.agreementEndFrom());
        if (query.agreementEndTo() != null) parts.add("agreement ends to " + query.agreementEndTo());
        parts.add("sorted by " + query.sortBy() + " " + query.sortDirection().toUpperCase());
        return String.join("  |  ", parts);
    }
}