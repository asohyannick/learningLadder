package com.learningLadder.learningLadder.service.shoppingCartService;

import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.courseEnrollment.CourseEnrollment;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.repository.courseEnrollmentRepository.CourseEnrollmentRepository;
import com.learningLadder.learningLadder.repository.courseRepository.CourseRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseCommerceAdapter {

    private static final String SOURCE_PURCHASE = "PURCHASE";

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public record CourseSnapshot(String title, String thumbnail, BigDecimal price,
                                 BigDecimal discount, String currency, boolean purchasable) {
    }

    public CourseSnapshot snapshotOf(Course course) {
        BigDecimal price = course.getPrice() == null ? BigDecimal.ZERO : course.getPrice();

        BigDecimal discount = course.getDiscount() == null ? BigDecimal.ZERO : course.getDiscount();
        discount = discount.max(BigDecimal.ZERO).min(price);

        StoredFile thumbnail = course.getThumbnail();
        String thumbnailKey = thumbnail == null ? null : thumbnail.getObjectKey();

        return new CourseSnapshot(
                course.getName(),
                thumbnailKey,
                price,
                discount,
                course.getCurrency() == null ? "USD" : course.getCurrency(),
                course.isPublished());
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyEnrolled(UUID userId, UUID courseId) {
        return enrollmentRepository.existsByUser_IdAndCourse_IdAndActiveTrue(userId, courseId);
    }

    public boolean isInstructorOfCourse(Course course, UUID userId) {
        return course.getCreatedBy() != null && course.getCreatedBy().equals(userId);
    }

    @Transactional
    public void grantCourseAccess(UUID userId, List<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        Map<UUID, CourseEnrollment> existing = enrollmentRepository.findByUser_IdAndCourse_IdIn(userId, courseIds)
                .stream()
                .collect(Collectors.toMap(enrollment -> enrollment.getCourse().getId(), Function.identity()));

        Instant now = Instant.now();
        List<CourseEnrollment> toSave = new ArrayList<>();

        for (UUID courseId : courseIds.stream().distinct().toList()) {
            CourseEnrollment enrollment = existing.get(courseId);
            if (enrollment == null) {
                enrollment = new CourseEnrollment();
                enrollment.setUser(userRepository.getReferenceById(userId));
                enrollment.setCourse(courseRepository.getReferenceById(courseId));
                enrollment.setSource(SOURCE_PURCHASE);
                enrollment.setGrantedAt(now);
                toSave.add(enrollment);
            } else if (!enrollment.isActive()) {
                enrollment.setActive(true);
                enrollment.setSource(SOURCE_PURCHASE);
                enrollment.setGrantedAt(now);
                enrollment.setRevokedAt(null);
                enrollment.setRevokeReason(null);
                toSave.add(enrollment);
            }
        }

        enrollmentRepository.saveAll(toSave);
        log.info("Granted access to {} course(s) for user {}", toSave.size(), userId);
    }


    @Transactional
    public void revokeCourseAccess(UUID userId, List<UUID> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        List<CourseEnrollment> revoked = enrollmentRepository.findByUser_IdAndCourse_IdIn(userId, courseIds)
                .stream()
                .filter(CourseEnrollment::isActive)
                .peek(enrollment -> {
                    enrollment.setActive(false);
                    enrollment.setRevokedAt(now);
                    enrollment.setRevokeReason("Payment refunded");
                })
                .toList();

        enrollmentRepository.saveAll(revoked);
        log.info("Revoked access to {} course(s) for user {}", revoked.size(), userId);
    }
}