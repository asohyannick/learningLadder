package com.learningLadder.learningLadder.service.reviewService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.minioConfig.MinioFileStorageService;
import com.learningLadder.learningLadder.config.resendConfig.emailService.EmailService;
import com.learningLadder.learningLadder.dto.reviewDto.CreateReviewRequestDto;
import com.learningLadder.learningLadder.dto.reviewDto.ReviewPagedQueryDto;
import com.learningLadder.learningLadder.dto.reviewDto.ReviewResponseDto;
import com.learningLadder.learningLadder.dto.reviewDto.UpdateReviewRequestDto;
import com.learningLadder.learningLadder.dto.userDto.PagedResponseDto;
import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.review.Review;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.fileCategory.FileCategory;
import com.learningLadder.learningLadder.enums.reviewStatus.ReviewStatus;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.mappers.reviewMapper.ReviewMapper;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.reviewRepository.ReviewRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ReviewService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 5_000;
    private static final int MIN_KEY_LENGTH = 8;
    private static final int MAX_KEY_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 500;

    private static final Set<UserRole> MODERATOR_ROLES = EnumSet.of(
            UserRole.SUPER_ADMIN, UserRole.ADMIN, UserRole.IT_ADMIN, UserRole.ACADEMIC_DIRECTOR);

    private final ReviewRepository reviewRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;
    private final ReviewPdfGenerator pdfGenerator;
    private final MinioFileStorageService minioFileStorageService;
    private final EmailService emailService;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;

    @CacheEvict(cacheNames = CacheNames.REVIEWS, allEntries = true)
    public ReviewResponseDto addReview(String idempotencyKey,
                                       CreateReviewRequestDto data,
                                       MultipartFile profilePicture) {
        String key = validateIdempotencyKey(idempotencyKey);
        User actor = currentUser();

        if (actor.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only students can review a course");
        }

        Optional<Review> replay = reviewRepository.findByIdempotencyKey(key);
        if (replay.isPresent()) {
            Review previous = replay.get();
            if (!Objects.equals(actor.getId(), previous.getStudent().getId())) {
                throw new BadRequestException("This idempotency key has already been used");
            }
            return reviewMapper.toResponse(previous);
        }

        Course course = courseRepository.findById(data.courseId())
                .orElseThrow(() -> new BadRequestException("Course not found"));

        if (reviewRepository.existsByCourse_IdAndStudent_Id(course.getId(), actor.getId())) {
            throw new BadRequestException("You have already reviewed this course");
        }

        if (profilePicture == null || profilePicture.isEmpty()) {
            throw new BadRequestException("A profile picture is required to submit a review");
        }

        StoredFile uploaded = minioFileStorageService.upload(profilePicture, FileCategory.IMAGE, actor.getId());
        try {
            Review entity = reviewMapper.toEntity(data);
            entity.setCourse(course);
            entity.setStudent(actor);
            entity.setProfilePic(uploaded.getPresignedUrl());
            entity.setIdempotencyKey(key);
            entity.setStatus(ReviewStatus.PENDING);

            return reviewMapper.toResponse(reviewRepository.saveAndFlush(entity));
        } catch (RuntimeException ex) {
            removeUploadQuietly(uploaded.getId());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.REVIEWS, key = "'all:' + #page + ':' + #size")
    public PagedResponseDto<ReviewResponseDto> fetchAllReviews(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

        return toPaged(reviewRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.REVIEWS, key = "'id:' + #id")
    public ReviewResponseDto fetchReview(UUID id) {
        return reviewMapper.toResponse(findOrThrow(id));
    }

    @CacheEvict(cacheNames = CacheNames.REVIEWS, allEntries = true)
    public ReviewResponseDto updateReview(UUID id, UpdateReviewRequestDto data, MultipartFile profilePicture) {
        User actor = currentUser();
        Review review = findOrThrow(id);
        requireOwnerOrModerator(review, actor);

        if (data != null) {
            reviewMapper.updateEntity(data, review);
            review.setTitle(blankToNull(review.getTitle()));
            review.setComment(blankToNull(review.getComment()));
        }

        StoredFile uploaded = null;
        if (profilePicture != null && !profilePicture.isEmpty()) {
            uploaded = minioFileStorageService.upload(profilePicture, FileCategory.IMAGE, actor.getId());
            review.setProfilePic(uploaded.getPresignedUrl());
        }

        try {
            return reviewMapper.toResponse(reviewRepository.saveAndFlush(review));
        } catch (RuntimeException ex) {
            if (uploaded != null) {
                removeUploadQuietly(uploaded.getId());
            }
            throw ex;
        }
    }

    @CacheEvict(cacheNames = CacheNames.REVIEWS, allEntries = true)
    public void deleteReview(UUID id) {
        User actor = currentUser();
        Review review = findOrThrow(id);
        requireOwnerOrModerator(review, actor);

        StoredFile picture = review.getProfilePicture();
        reviewRepository.delete(review);
        if (picture != null) {
            removeUploadQuietly(picture.getId());
        }
    }

    @CacheEvict(cacheNames = CacheNames.REVIEWS, allEntries = true)
    public ReviewResponseDto approveReview(UUID id, String reason) {
        User moderator = currentUser();
        requireModerator(moderator);

        Review review = findOrThrow(id);
        String cleanReason = blankToNull(reason == null ? null : reason.trim());
        if (cleanReason != null && cleanReason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }
        if (review.getStatus() == ReviewStatus.APPROVED) {
            throw new BadRequestException("Review is already approved");
        }

        ReviewResponseDto response = moderate(review, ReviewStatus.APPROVED, null, moderator);

        notifyStudent(review, "Your review was approved",
                "Your review of \"" + review.getCourse().getName() + "\" has been approved and is now visible.");
        return response;
    }

    @CacheEvict(cacheNames = CacheNames.REVIEWS, allEntries = true)
    public ReviewResponseDto disapproveReview(UUID id, String reason) {
        User moderator = currentUser();
        requireModerator(moderator);

        String cleanReason = blankToNull(reason == null ? null : reason.trim());
        if (cleanReason != null && cleanReason.length() > MAX_REASON_LENGTH) {
            throw new BadRequestException("Reason must not exceed " + MAX_REASON_LENGTH + " characters");
        }

        Review review = findOrThrow(id);
        if (review.getStatus() == ReviewStatus.REJECTED) {
            throw new BadRequestException("Review is already disapproved");
        }

        ReviewResponseDto response = moderate(review, ReviewStatus.REJECTED, cleanReason, moderator);

        String message = "Your review of \"" + review.getCourse().getName() + "\" was not approved."
                + (cleanReason == null ? "" : " Reason: " + cleanReason);
        notifyStudent(review, "Your review was not approved", message);
        return response;
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.REVIEWS, key = "'download:' + #query")
    public byte[] downloadReviews(ReviewPagedQueryDto query) {
        Specification<Review> specification = buildSpecification(query);

        long total = reviewRepository.count(specification);
        if (total == 0) {
            throw new BadRequestException("No reviews match the selected filters");
        }
        if (total > MAX_EXPORT_ROWS) {
            throw new BadRequestException("Too many reviews to export (" + total
                    + "). Narrow your filters to at most " + MAX_EXPORT_ROWS + ".");
        }

        List<ReviewResponseDto> rows = reviewRepository.findAll(specification, sortFor(query)).stream()
                .map(reviewMapper::toResponse)
                .toList();

        return pdfGenerator.generate(rows, describeFilters(query));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.REVIEWS, key = "'count'")
    public long countReviews() {
        return reviewRepository.count();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.REVIEWS, key = "'search:' + #query")
    public PagedResponseDto<ReviewResponseDto> searchReviews(ReviewPagedQueryDto query) {
        Pageable pageable = PageRequest.of(
                query.page(),
                Math.clamp(query.size(), 1, MAX_PAGE_SIZE),
                sortFor(query));

        return toPaged(reviewRepository.findAll(buildSpecification(query), pageable));
    }

    private ReviewResponseDto moderate(Review review, ReviewStatus status, String note, User moderator) {
        review.setStatus(status);
        review.setModerationNote(note);
        review.setModeratedBy(moderator.getId());
        review.setModeratedAt(Instant.now());
        return reviewMapper.toResponse(reviewRepository.saveAndFlush(review));
    }

    private void notifyStudent(Review review, String subject, String message) {
        try {
            User student = review.getStudent();
            if (student == null || isBlank(student.getEmail())) {
                return;
            }
            String to = student.getEmail();
            String name = isBlank(student.getFirstName()) ? "there" : student.getFirstName();

            String html = "<p>Hi " + HtmlUtils.htmlEscape(name) + ",</p>"
                    + "<p>" + HtmlUtils.htmlEscape(message) + "</p>"
                    + "<p>The Learning Ladder team</p>";
            String text = "Hi " + name + ",\n\n" + message + "\n\nThe Learning Ladder team";

            fireAndForgetEmailAsync.fire(subject + " " + review.getId(),
                    () -> emailService.send(List.of(to), subject, html, text));
        } catch (Exception ex) {
            log.warn("Could not queue email for review {}: {}", review.getId(), ex.getMessage());
        }
    }

    private void removeUploadQuietly(UUID fileId) {
        try {
            minioFileStorageService.delete(fileId);
        } catch (Exception ex) {
            log.warn("Could not clean up uploaded profile picture {}: {}", fileId, ex.getMessage());
        }
    }

    private Specification<Review> buildSpecification(ReviewPagedQueryDto q) {
        List<Specification<Review>> filters = new ArrayList<>();

        if (q.keyword() != null) {
            String pattern = "%" + escapeLike(q.keyword().toLowerCase(Locale.ROOT)) + "%";
            filters.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.<String>get("comment")), pattern, '\\'),
                    cb.like(cb.lower(root.get("course").<String>get("name")), pattern, '\\'),
                    cb.like(cb.lower(root.get("student").<String>get("firstName")), pattern, '\\'),
                    cb.like(cb.lower(root.get("student").<String>get("lastName")), pattern, '\\')));
        }
        if (q.courseId() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("course").get("id"), q.courseId()));
        }
        if (q.studentId() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("student").get("id"), q.studentId()));
        }
        if (q.rating() != null) {
            filters.add((root, query, cb) -> cb.equal(root.<Integer>get("rating"), q.rating()));
        }
        if (q.minRating() != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Integer>get("rating"), q.minRating()));
        }
        if (q.maxRating() != null) {
            filters.add((root, query, cb) -> cb.lessThanOrEqualTo(root.<Integer>get("rating"), q.maxRating()));
        }
        if (q.status() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), q.status()));
        }
        if (q.verifiedEnrollment() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("verifiedEnrollment"), q.verifiedEnrollment()));
        }
        if (q.hasReply() != null) {
            filters.add((root, query, cb) -> q.hasReply()
                    ? cb.isNotNull(root.get("tutorReply"))
                    : cb.isNull(root.get("tutorReply")));
        }
        if (q.fromDate() != null) {
            Instant from = q.fromDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
        }
        if (q.toDate() != null) {
            Instant before = q.toDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            filters.add((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), before));
        }

        return Specification.allOf(filters);
    }

    private Sort sortFor(ReviewPagedQueryDto q) {
        Sort.Direction direction = "asc".equals(q.sortDirection()) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, q.sortBy()).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private List<String> describeFilters(ReviewPagedQueryDto q) {
        List<String> filters = new ArrayList<>();
        if (q.keyword() != null) filters.add("keyword \"" + q.keyword() + "\"");
        if (q.courseId() != null) filters.add("course " + q.courseId());
        if (q.studentId() != null) filters.add("student " + q.studentId());
        if (q.rating() != null) filters.add("rating " + q.rating());
        if (q.minRating() != null) filters.add("rating >= " + q.minRating());
        if (q.maxRating() != null) filters.add("rating <= " + q.maxRating());
        if (q.status() != null) filters.add("status " + q.status());
        if (q.verifiedEnrollment() != null) {
            filters.add(q.verifiedEnrollment() ? "verified enrollment only" : "unverified only");
        }
        if (q.hasReply() != null) filters.add(q.hasReply() ? "with tutor reply" : "without tutor reply");
        if (q.fromDate() != null) filters.add("from " + q.fromDate());
        if (q.toDate() != null) filters.add("to " + q.toDate());
        return filters;
    }

    private PagedResponseDto<ReviewResponseDto> toPaged(Page<Review> page) {
        return PagedResponseDto.from(page, reviewMapper::toResponse);
    }

    private String validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("The Idempotency-Key header is required");
        }
        String trimmed = key.trim();
        if (trimmed.length() < MIN_KEY_LENGTH || trimmed.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("The Idempotency-Key must be between "
                    + MIN_KEY_LENGTH + " and " + MAX_KEY_LENGTH + " characters");
        }
        return trimmed;
    }

    private Review findOrThrow(UUID id) {
        return reviewRepository.findById(id).orElseThrow(() -> new BadRequestException("Review not found"));
    }

    private void requireOwnerOrModerator(Review review, User actor) {
        boolean owner = Objects.equals(review.getStudent().getId(), actor.getId());
        if (!owner && !isModerator(actor)) {
            throw new AccessDeniedException("You can only modify your own reviews");
        }
    }

    private void requireModerator(User actor) {
        if (!isModerator(actor)) {
            throw new AccessDeniedException("You do not have permission to moderate reviews");
        }
    }

    private static boolean isModerator(User user) {
        return user.getRole() != null && MODERATOR_ROLES.contains(user.getRole());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BadRequestException("Authentication is required");
        }
        String name = authentication.getName();

        Optional<User> user;
        try {
            user = userRepository.findById(UUID.fromString(name));
        } catch (IllegalArgumentException notAUuid) {
            user = userRepository.findByEmail(name);
        }
        return user.orElseThrow(() -> new BadRequestException("User account not found"));
    }

    private ReviewResponseDto toResponse(Review review) {
        ReviewResponseDto dto = reviewMapper.toResponse(review);
        if (review.getProfilePicture() == null) {
            return dto;
        }
        return dto.withStudentProfilePic(minioFileStorageService.resolveUrl(review.getProfilePicture()));
    }
}