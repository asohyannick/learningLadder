package com.learningLadder.learningLadder.repository.courseRepository;

import com.learningLadder.learningLadder.entity.course.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    boolean existsBySlug(String slug);

    @EntityGraph(attributePaths = {"thumbnail", "promoVideo", "syllabus", "materials", "materials.file"})
    Optional<Course> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"thumbnail", "promoVideo", "syllabus", "materials", "materials.file"})
    Optional<Course> findWithDetailsByIdempotencyKey(String idempotencyKey);

    @Override
    @EntityGraph(attributePaths = "thumbnail")
    Page<Course> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "thumbnail")
    Page<Course> findAll(Specification<Course> spec, Pageable pageable);
}