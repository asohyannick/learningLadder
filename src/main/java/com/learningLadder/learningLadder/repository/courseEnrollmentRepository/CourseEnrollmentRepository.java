package com.learningLadder.learningLadder.repository.courseEnrollmentRepository;

import com.learningLadder.learningLadder.entity.courseEnrollment.CourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {

    boolean existsByUser_IdAndCourse_IdAndActiveTrue(UUID userId, UUID courseId);

    List<CourseEnrollment> findByUser_IdAndCourse_IdIn(UUID userId, Collection<UUID> courseIds);
}