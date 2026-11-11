package com.learningLadder.learningLadder.service.shoppingCartService;

import com.learningLadder.learningLadder.entity.course.Course;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class CourseCommerceAdapter {

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


    public boolean isAlreadyEnrolled(UUID userId, UUID courseId) {
        return false;
    }

    public boolean isInstructorOfCourse(Course course, UUID userId) {
        return course.getCreatedBy() != null && course.getCreatedBy().equals(userId);
    }
}