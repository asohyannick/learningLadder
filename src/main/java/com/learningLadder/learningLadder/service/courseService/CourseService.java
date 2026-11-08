package com.learningLadder.learningLadder.service.courseService;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.courseDto.*;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.course.CourseMaterial;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.mappers.courseMapper.CourseMapper;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.courseRepository.CourseSpecifications;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CourseService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final CourseRepository courseRepository;
    private final MinioFileStorageService storage;
    private final CourseMapper courseMapper;
    private final TransactionTemplate transactionTemplate;

    // ------------------------------------------------------------------ 1. CREATE

    /**
     * Idempotent: sending the same key again returns the course created the first time
     * instead of creating a duplicate. If no key is supplied, one is generated.
     * Deliberately NOT @Transactional so slow uploads never hold a DB connection.
     */
    @CacheEvict(cacheNames = CacheNames.COURSES, allEntries = true)
    public CourseResponseDto createCourse(CreateCourseRequestDto request,
                                          String idempotencyKey,
                                          MultipartFile thumbnail,
                                          MultipartFile promoVideo,
                                          MultipartFile syllabus,
                                          List<MultipartFile> materialFiles,
                                          UUID userId) {

        String key = resolveIdempotencyKey(idempotencyKey);

        Optional<Course> existing = courseRepository.findWithDetailsByIdempotencyKey(key);
        if (existing.isPresent()) {
            return courseMapper.toResponse(existing.get());
        }

        List<StoredFile> uploaded = new ArrayList<>();
        try {
            Course course = courseMapper.toEntity(request);
            course.setIdempotencyKey(key);
            course.setSlug(uniqueSlug(request.name()));
            course.setCreatedBy(userId);
            if (course.isPublished()) {
                course.setPublishedAt(Instant.now());
            }

            course.setThumbnail(uploadIfPresent(thumbnail, FileCategory.IMAGE, userId, uploaded));
            course.setPromoVideo(uploadIfPresent(promoVideo, FileCategory.VIDEO, userId, uploaded));
            course.setSyllabus(uploadIfPresent(syllabus, FileCategory.DOCUMENT, userId, uploaded));

            int order = 0;
            for (MultipartFile file : materialFiles == null ? List.<MultipartFile>of() : materialFiles) {
                if (file == null || file.isEmpty()) continue;

                StoredFile stored = storage.upload(file, FileCategory.fromContentType(file.getContentType()), userId);
                uploaded.add(stored);

                CourseMaterial material = new CourseMaterial();
                material.setFile(stored);
                material.setTitle(stored.getOriginalFileName());   // the user can rename it later
                material.setSortOrder(order++);
                course.addMaterial(material);
            }

            return courseMapper.toResponse(courseRepository.save(course));

        } catch (DataIntegrityViolationException ex) {
            // Two requests with the same key raced: the other one won. Discard our files, return its course.
            deleteUploadedQuietly(uploaded);
            return courseRepository.findWithDetailsByIdempotencyKey(key)
                    .map(courseMapper::toResponse)
                    .orElseThrow(() -> ex);
        } catch (RuntimeException ex) {
            deleteUploadedQuietly(uploaded);
            throw ex;
        }
    }


    @Cacheable(cacheNames = CacheNames.COURSES, key = "'all:' + #page + ':' + #size")
    @Transactional(readOnly = true)
    public PagedResponseDto<CourseSummaryResponseDto> fetchAllCourses(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PagedResponseDto.from(courseRepository.findAll(pageable).map(courseMapper::toSummary));
    }

    @Cacheable(cacheNames = CacheNames.COURSES, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public CourseResponseDto fetchCourse(UUID id) {
        return courseMapper.toResponse(getCourseWithDetails(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.COURSES, allEntries = true)
    public CourseResponseDto updateCourse(UUID id, UpdateCourseRequestDto request) {
        Course course = getCourseWithDetails(id);
        courseMapper.updateEntity(request, course);

        if (course.getPrice() != null && course.getDiscount() != null
                && course.getDiscount().compareTo(course.getPrice()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount cannot exceed price");
        }
        if (course.isPublished() && course.getPublishedAt() == null) {
            course.setPublishedAt(Instant.now());
        }
        return courseMapper.toResponse(course);   // dirty-checking flushes the changes at commit
    }


    @CacheEvict(cacheNames = CacheNames.COURSES, allEntries = true)
    public void deleteCourse(UUID id) {
        Set<UUID> fileIds = transactionTemplate.execute(status -> {
            Course course = getCourseWithDetails(id);

            Set<UUID> ids = new LinkedHashSet<>();
            addFileId(ids, course.getThumbnail());
            addFileId(ids, course.getPromoVideo());
            addFileId(ids, course.getSyllabus());
            course.getMaterials().forEach(m -> addFileId(ids, m.getFile()));

            courseRepository.delete(course);
            return ids;
        });

        if (fileIds != null) {
            fileIds.forEach(this::deleteFileQuietly);
        }
    }

    @Cacheable(cacheNames = CacheNames.COURSES, key = "'search:' + #query.cacheKey()")
    @Transactional(readOnly = true)
    public PagedResponseDto<CourseSummaryResponseDto> searchCourses(CoursePagedQueryDto query) {
        Sort sort = Sort.by(Sort.Direction.fromString(query.sortDirection()), query.sortBy())
                .and(Sort.by("id"));
        Pageable pageable = PageRequest.of(query.page(), query.size(), sort);

        Page<Course> page = courseRepository.findAll(CourseSpecifications.from(query), pageable);
        return PagedResponseDto.from(page.map(courseMapper::toSummary));
    }

    @Cacheable(cacheNames = CacheNames.COURSES, key = "'count'")
    @Transactional(readOnly = true)
    public Long countCourses() {
        return courseRepository.count();
    }


    @Cacheable(cacheNames = CacheNames.COURSES, key = "'pdf:' + #id")
    @Transactional(readOnly = true)
    public CoursePdfDto downloadCoursePdf(UUID id) {
        CourseResponseDto course = courseMapper.toResponse(getCourseWithDetails(id));
        try {
            return new CoursePdfDto(course.slug() + ".pdf", renderPdf(course));
        } catch (IOException e) {
            throw new BadRequestException("Failed to generate course PDF", e);
        }
    }

    private byte[] renderPdf(CourseResponseDto c) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PdfCursor pdf = new PdfCursor(doc);

            pdf.line(c.name(), pdf.bold, 20);
            pdf.gap(8);
            pdf.field("Category", c.category());
            pdf.field("Level", c.level() == null ? null : c.level().name());
            pdf.field("Language", c.language());
            pdf.field("Duration", c.durationMinutes() == null ? null : c.durationMinutes() + " minutes");
            pdf.field("Price", c.price() == null ? null : c.price() + " " + c.currency());
            pdf.field("Discount", c.discount() == null ? null : c.discount() + " " + c.currency());
            pdf.field("Status", c.status() == null ? null : c.status().name());
            pdf.field("Published", c.published() ? "Yes" : "No");

            pdf.section("Overview", c.shortDescription());
            pdf.section("Description", c.description());
            pdf.section("Learning outcomes", c.learningOutcomes());
            pdf.section("Requirements", c.requirements());

            if (c.materials() != null && !c.materials().isEmpty()) {
                pdf.gap(10);
                pdf.line("Course materials", pdf.bold, 13);
                for (CourseMaterialResponseDto m : c.materials()) {
                    String name = m.fileName() == null ? "" : " (" + m.fileName() + ")";
                    pdf.line((m.sortOrder() + 1) + ". " + m.title() + name, pdf.regular, 11);
                }
            }

            pdf.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private Course getCourseWithDetails(UUID id) {
        return courseRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
    }

    private String resolveIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must not exceed " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    private StoredFile uploadIfPresent(MultipartFile file, FileCategory category, UUID userId, List<StoredFile> uploaded) {
        if (file == null || file.isEmpty()) return null;
        StoredFile stored = storage.upload(file, category, userId);
        uploaded.add(stored);
        return stored;
    }

    private void addFileId(Set<UUID> ids, StoredFile file) {
        if (file != null) ids.add(file.getId());
    }

    private void deleteUploadedQuietly(List<StoredFile> files) {
        files.forEach(f -> deleteFileQuietly(f.getId()));
    }

    private void deleteFileQuietly(UUID fileId) {
        try { storage.delete(fileId); } catch (Exception ignored) { }
    }

    private String uniqueSlug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String base = ascii.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "course";
        if (base.length() > 200) base = base.substring(0, 200).replaceAll("-$", "");

        String slug = base;
        int counter = 2;
        while (courseRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }

    private static final class PdfCursor {
        private static final float MARGIN = 50f;

        final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        private final PDDocument doc;
        private final float usableWidth = PDRectangle.A4.getWidth() - 2 * MARGIN;
        private PDPageContentStream stream;
        private float y;

        PdfCursor(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        void gap(float points) throws IOException {
            if (y - points < MARGIN) newPage(); else y -= points;
        }

        void field(String label, String value) throws IOException {
            if (value == null || value.isBlank()) return;
            line(label + ": " + value, regular, 11);
        }

        void section(String title, String text) throws IOException {
            if (text == null || text.isBlank()) return;
            gap(10);
            line(title, bold, 13);
            line(text, regular, 11);
        }

        void line(String text, PDType1Font font, float size) throws IOException {
            float leading = size * 1.4f;
            for (String paragraph : text.split("\\R", -1)) {
                for (String wrapped : wrap(sanitize(paragraph, font), font, size)) {
                    if (y - leading < MARGIN) newPage();
                    stream.beginText();
                    stream.setFont(font, size);
                    stream.newLineAtOffset(MARGIN, y - size);
                    stream.showText(wrapped);
                    stream.endText();
                    y -= leading;
                }
            }
        }

        void finish() throws IOException {
            stream.close();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        private String sanitize(String text, PDType1Font font) {
            StringBuilder sb = new StringBuilder(text.length());
            for (char ch : text.toCharArray()) {
                if (ch == '\t') { sb.append(' '); continue; }
                try {
                    font.encode(String.valueOf(ch));
                    sb.append(ch);
                } catch (IllegalArgumentException | IOException e) {
                    sb.append('?');
                }
            }
            return sb.toString();
        }

        private List<String> wrap(String text, PDType1Font font, float size) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(candidate, font, size) <= usableWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                String rest = word;
                while (width(rest, font, size) > usableWidth) {
                    int cut = rest.length();
                    while (cut > 1 && width(rest.substring(0, cut), font, size) > usableWidth) cut--;
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                current.append(rest);
            }
            lines.add(current.toString());
            return lines;
        }

        private float width(String s, PDType1Font font, float size) throws IOException {
            return font.getStringWidth(s) / 1000f * size;
        }
    }
}