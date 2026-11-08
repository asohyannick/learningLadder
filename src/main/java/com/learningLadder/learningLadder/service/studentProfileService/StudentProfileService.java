package com.learningLadder.learningLadder.service.studentProfileService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.dto.studentProfileDto.CreateStudentProfileRequestDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.StudentProfilePagedQueryDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.StudentProfileResponseDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.StudentProfileSummaryResponseDto;
import com.learningLadder.learningLadder.dto.studentProfileDto.UpdateStudentProfileRequestDto;
import com.learningLadder.learningLadder.dto.userDto.PagedResponseDto;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.enrollmentStatus.EnrollmentStatus;
import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.mappers.studentProfileMapper.StudentProfileMapper;
import com.learningLadder.learningLadder.repository.studentProfileRepository.StudentProfileRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final MinioFileStorageService minioFileStorageService;
    private final StudentProfileMapper studentProfileMapper;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;
    private final UserRepository userRepository;

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{8,100}$");
    private static final Set<UserRole> ADMIN_ROLES = EnumSet.of(UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.IT_ADMIN);
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "firstName", "lastName", "email", "studentNumber",
            "currentLevel", "admissionDate", "createdAt", "updatedAt");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 5_000;

    private static final DateTimeFormatter REPORT_TIME = DateTimeFormatter
            .ofPattern("dd MMM yyyy, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private static final float PDF_MARGIN = 36f;
    private static final float PDF_ROW_HEIGHT = 18f;
    private static final float PDF_FONT_SIZE = 8.5f;


    @CacheEvict(cacheNames = CacheNames.STUDENTS, allEntries = true)
    public StudentProfileResponseDto createStudentProfile(String idempotencyKey,
                                                          CreateStudentProfileRequestDto dto,
                                                          MultipartFile profilePicture) {
        String key = normalizeKey(idempotencyKey);
        User user = currentUser();

        if (user.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Only student accounts can create a student profile");
        }

        Optional<StudentProfile> replay = studentProfileRepository.findByIdempotencyKey(key);
        if (replay.isPresent()) {
            StudentProfile existing = replay.get();
            if (!existing.getUser().getId().equals(user.getId())) {
                throw new BadRequestException("This idempotency key has already been used");
            }
            return toResponse(existing);
        }

        if (studentProfileRepository.existsByUserId(user.getId())) {
            throw new BadRequestException("You already have a student profile");
        }

        StudentProfile profile = studentProfileMapper.toEntity(dto);
        profile.setUser(user);
        profile.setEmail(user.getEmail());
        profile.setIdempotencyKey(key);
        profile.setEnrollmentStatus(EnrollmentStatus.APPLICANT);

        StoredFile uploaded = null;
        if (hasFile(profilePicture)) {
            uploaded = minioFileStorageService.upload(profilePicture, FileCategory.IMAGE, user.getId());
            profile.setProfilePicture(uploaded);
        }

        StudentProfile saved;
        try {
            saved = studentProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            removeFileQuietly(uploaded);
            throw new BadRequestException("A student profile already exists for this account or request. Please retry.");
        }

        return toResponse(saved);
    }

    @Cacheable(cacheNames = CacheNames.STUDENTS, key = "'all:' + #page + '|' + #size")
    @Transactional(readOnly = true)
    public PagedResponseDto<StudentProfileSummaryResponseDto> fetchAllStudentProfiles(int page, int size) {
        StudentProfilePagedQueryDto query = new StudentProfilePagedQueryDto(
                null, null, null, null, null, null, null, null, page, size, null, null);
        return runSearch(query);
    }

    @Cacheable(cacheNames = CacheNames.STUDENTS, key = "'id:' + #id")
    @Transactional(readOnly = true)
    public StudentProfileResponseDto fetchStudentProfile(UUID id) {
        return toResponse(findProfile(id));
    }

    @CacheEvict(cacheNames = CacheNames.STUDENTS, allEntries = true)
    public StudentProfileResponseDto updateStudentProfile(UUID id,
                                                          UpdateStudentProfileRequestDto dto,
                                                          MultipartFile profilePicture) {
        User caller = currentUser();
        StudentProfile profile = findProfile(id);
        assertOwnerOrAdmin(profile, caller);

        studentProfileMapper.updateEntity(dto, profile);

        StoredFile oldPicture = profile.getProfilePicture();
        StoredFile uploaded = null;
        if (hasFile(profilePicture)) {
            uploaded = minioFileStorageService.upload(profilePicture, FileCategory.IMAGE, caller.getId());
            profile.setProfilePicture(uploaded);
        }

        StudentProfile saved;
        try {
            saved = studentProfileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            removeFileQuietly(uploaded);
            throw new BadRequestException("Could not update the student profile. Please check the values and retry.");
        }

        if (uploaded != null && oldPicture != null) {
            removeFileQuietly(oldPicture);
        }

        return toResponse(saved);
    }

    @CacheEvict(cacheNames = CacheNames.STUDENTS, allEntries = true)
    public void deleteStudentProfile(UUID id) {
        User caller = currentUser();
        StudentProfile profile = findProfile(id);
        assertOwnerOrAdmin(profile, caller);

        StoredFile picture = profile.getProfilePicture();

        studentProfileRepository.delete(profile);
        studentProfileRepository.flush();

        removeFileQuietly(picture);
    }

    @Cacheable(cacheNames = CacheNames.STUDENTS, key = "'count'")
    @Transactional(readOnly = true)
    public long countStudentProfiles() {
        return studentProfileRepository.count();
    }

    @Transactional(readOnly = true)
    public byte[] downloadStudentProfiles(StudentProfilePagedQueryDto filter) {
        StudentProfilePagedQueryDto query = filter != null ? filter : new StudentProfilePagedQueryDto(
                null, null, null, null, null, null, null, null, 0, DEFAULT_PAGE_SIZE, null, null);

        Specification<StudentProfile> spec = buildSpecification(query);

        long total = studentProfileRepository.count(spec);
        if (total == 0) {
            throw new BadRequestException("No student profiles match the selected filters");
        }
        if (total > MAX_EXPORT_ROWS) {
            throw new BadRequestException("Too many profiles to export (" + total
                    + "). Narrow your filters to at most " + MAX_EXPORT_ROWS + ".");
        }

        List<StudentProfile> rows = studentProfileRepository.findAll(spec, sortFor(query));
        return buildPdf(rows);
    }

    @Cacheable(cacheNames = CacheNames.STUDENTS, key = "'search:' + #query.cacheKey()")
    @Transactional(readOnly = true)
    public PagedResponseDto<StudentProfileSummaryResponseDto> searchStudentProfiles(StudentProfilePagedQueryDto query) {
        return runSearch(query);
    }

    private PagedResponseDto<StudentProfileSummaryResponseDto> runSearch(StudentProfilePagedQueryDto q) {
        int page = Math.max(q.page(), 0);
        int size = q.size() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(q.size(), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(page, size, sortFor(q));

        Page<StudentProfile> result = studentProfileRepository.findAll(buildSpecification(q), pageable);

        List<StudentProfileSummaryResponseDto> content = new ArrayList<>();
        for (StudentProfile profile : result.getContent()) {
            content.add(toSummary(profile));
        }

        return new PagedResponseDto<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getNumberOfElements(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                result.hasNext(),
                result.hasPrevious());
    }

    private Sort sortFor(StudentProfilePagedQueryDto q) {
        String sortBy = SORTABLE_FIELDS.contains(q.sortBy()) ? q.sortBy() : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(q.sortDirection())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private Specification<StudentProfile> buildSpecification(StudentProfilePagedQueryDto q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(q.search())) {
                String pattern = "%" + escapeLike(q.search().trim().toLowerCase(Locale.ROOT)) + "%";
                Expression<String> fullName = cb.lower(cb.concat(
                        cb.concat(root.<String>get("firstName"), " "),
                        root.<String>get("lastName")));
                predicates.add(cb.or(
                        cb.like(fullName, pattern, '\\'),
                        cb.like(cb.lower(root.<String>get("email")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("studentNumber"), "")), pattern, '\\')));
            }
            if (hasText(q.faculty())) {
                predicates.add(cb.equal(cb.lower(root.<String>get("faculty")),
                        q.faculty().trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(q.department())) {
                predicates.add(cb.equal(cb.lower(root.<String>get("department")),
                        q.department().trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(q.programOfStudy())) {
                predicates.add(cb.equal(cb.lower(root.<String>get("programOfStudy")),
                        q.programOfStudy().trim().toLowerCase(Locale.ROOT)));
            }
            if (q.currentLevel() != null) {
                predicates.add(cb.equal(root.get("currentLevel"), q.currentLevel()));
            }
            if (q.enrollmentStatus() != null) {
                predicates.add(cb.equal(root.get("enrollmentStatus"), q.enrollmentStatus()));
            }
            if (q.admittedFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("admissionDate"), q.admittedFrom()));
            }
            if (q.admittedTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("admissionDate"), q.admittedTo()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private byte[] buildPdf(List<StudentProfile> rows) {
        final PDRectangle pageSize = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // landscape
        final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final String[] headers = {"#", "Name", "Email", "Student No.", "Faculty", "Department", "Level", "Status"};
        final float[] widths = {24f, 135f, 170f, 75f, 100f, 110f, 36f, 75f};

        try (PDDocument document = new PDDocument()) {
            PDPageContentStream cs = null;
            float y = 0f;
            int pageNumber = 0;
            int rowNumber = 0;

            for (StudentProfile p : rows) {
                if (cs == null || y - PDF_ROW_HEIGHT < PDF_MARGIN) {
                    if (cs != null) {
                        drawFooter(cs, regular, pageSize, pageNumber);
                        cs.close();
                    }
                    PDPage page = new PDPage(pageSize);
                    document.addPage(page);
                    pageNumber++;
                    cs = new PDPageContentStream(document, page);
                    y = pageSize.getHeight() - PDF_MARGIN;

                    if (pageNumber == 1) {
                        drawText(cs, bold, 16f, PDF_MARGIN, y - 12f, "Student Profiles Report");
                        drawText(cs, regular, 9f, PDF_MARGIN, y - 28f,
                                "Generated " + REPORT_TIME.format(Instant.now()) + "  |  " + rows.size() + " record(s)");
                        y -= 44f;
                    }
                    y = drawHeaderRow(cs, bold, headers, widths, y);
                }

                rowNumber++;
                String[] cells = {
                        String.valueOf(rowNumber),
                        p.getFirstName() + " " + p.getLastName(),
                        p.getEmail(),
                        p.getStudentNumber(),
                        p.getFaculty(),
                        p.getDepartment(),
                        p.getCurrentLevel() == null ? "" : String.valueOf(p.getCurrentLevel()),
                        p.getEnrollmentStatus() == null ? "" : p.getEnrollmentStatus().name()
                };
                drawDataRow(cs, regular, widths, y, cells, rowNumber % 2 == 0);
                y -= PDF_ROW_HEIGHT;
            }

            if (cs != null) {
                drawFooter(cs, regular, pageSize, pageNumber);
                cs.close();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate the student profiles PDF", ex);
        }
    }

    private float drawHeaderRow(PDPageContentStream cs, PDFont font, String[] headers,
                                float[] widths, float y) throws IOException {
        final float height = 20f;
        cs.setNonStrokingColor(0.31f, 0.27f, 0.90f);
        cs.addRect(PDF_MARGIN, y - height, sum(widths), height);
        cs.fill();

        cs.setNonStrokingColor(1f, 1f, 1f);
        float x = PDF_MARGIN;
        for (int i = 0; i < headers.length; i++) {
            drawText(cs, font, 9f, x + 4f, y - 13.5f, headers[i]);
            x += widths[i];
        }
        cs.setNonStrokingColor(0f, 0f, 0f);
        return y - height;
    }

    private void drawDataRow(PDPageContentStream cs, PDFont font, float[] widths, float y,
                             String[] cells, boolean shaded) throws IOException {
        if (shaded) {
            cs.setNonStrokingColor(0.95f, 0.95f, 0.98f);
            cs.addRect(PDF_MARGIN, y - PDF_ROW_HEIGHT, sum(widths), PDF_ROW_HEIGHT);
            cs.fill();
        }
        cs.setNonStrokingColor(0f, 0f, 0f);

        float x = PDF_MARGIN;
        for (int i = 0; i < cells.length; i++) {
            String text = fit(font, PDF_FONT_SIZE, pdfSafe(font, cells[i]), widths[i] - 8f);
            drawText(cs, font, PDF_FONT_SIZE, x + 4f, y - 12f, text);
            x += widths[i];
        }
    }

    private void drawFooter(PDPageContentStream cs, PDFont font, PDRectangle pageSize, int pageNumber)
            throws IOException {
        cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
        drawText(cs, font, 8f, pageSize.getWidth() - PDF_MARGIN - 40f, 20f, "Page " + pageNumber);
        cs.setNonStrokingColor(0f, 0f, 0f);
    }

    private void drawText(PDPageContentStream cs, PDFont font, float size, float x, float y, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private String pdfSafe(PDFont font, String text) {
        if (text == null) return "";
        StringBuilder safe = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                safe.append(' ');
                return;
            }
            String s = new String(Character.toChars(codePoint));
            try {
                font.encode(s);
                safe.append(s);
            } catch (Exception ex) {
                safe.append('?');
            }
        });
        return safe.toString();
    }

    private String fit(PDFont font, float size, String text, float maxWidth) throws IOException {
        if (text.isEmpty() || textWidth(font, size, text) <= maxWidth) return text;
        String ellipsis = "...";
        String trimmed = text;
        while (!trimmed.isEmpty() && textWidth(font, size, trimmed + ellipsis) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : trimmed + ellipsis;
    }

    private float textWidth(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    private float sum(float[] values) {
        float total = 0f;
        for (float v : values) total += v;
        return total;
    }

    private StudentProfileResponseDto toResponse(StudentProfile profile) {
        refreshPictureUrl(profile);
        return studentProfileMapper.toResponse(profile);
    }

    private StudentProfileSummaryResponseDto toSummary(StudentProfile profile) {
        refreshPictureUrl(profile);
        return studentProfileMapper.toSummary(profile);
    }

    private void refreshPictureUrl(StudentProfile profile) {
        StoredFile picture = profile.getProfilePicture();
        if (picture == null) return;

        Instant soon = Instant.now().plus(Duration.ofHours(24));
        boolean needsRefresh = picture.getPresignedUrl() == null
                || picture.getPresignedUrlExpiresAt() == null
                || picture.getPresignedUrlExpiresAt().isBefore(soon);
        if (!needsRefresh) return;

        try {
            minioFileStorageService.resolveUrl(picture);
        } catch (RuntimeException ex) {
            log.warn("Could not refresh profile picture URL [profileId={}]: {}", profile.getId(), ex.getMessage());
        }
    }

    private void removeFileQuietly(StoredFile file) {
        if (file == null) return;
        try {
            minioFileStorageService.delete(file.getId());
        } catch (Exception ex) {
            log.warn("Could not remove stored file [{}]: {}", file.getId(), ex.getMessage());
        }
    }

    private StudentProfile findProfile(UUID id) {
        return studentProfileRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Student profile not found"));
    }

    private void assertOwnerOrAdmin(StudentProfile profile, User caller) {
        boolean owner = profile.getUser().getId().equals(caller.getId());
        if (!owner && !ADMIN_ROLES.contains(caller.getRole())) {
            throw new BadRequestException("You can only manage your own student profile");
        }
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            throw new BadRequestException("You must be logged in to perform this action");
        }
        return userRepository.findByEmailIgnoreCase(auth.getName())
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("Authenticated user not found"));
    }

    private String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key is required");
        }
        String trimmed = key.trim();
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException(
                    "Idempotency-Key must be 8-100 characters: letters, digits, '.', '_', ':' or '-'");
        }
        return trimmed;
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}