package com.learningLadder.learningLadder.repository.reviewRepository;

import com.learningLadder.learningLadder.entity.review.Review;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, JpaSpecificationExecutor<Review> {

    @Override
    @EntityGraph(attributePaths = {"course", "student"})
    @NonNull
    Optional<Review> findById(@NonNull UUID id);

    @Override
    @EntityGraph(attributePaths = {"course", "student"})
    @NonNull
    Page<Review> findAll(@NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"course", "student"})
    @NonNull
    Page<Review> findAll(@NonNull Specification<Review> specification, @NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"course", "student"})
    @NonNull
    List<Review> findAll(@NonNull Specification<Review> specification, @NonNull Sort sort);

    @EntityGraph(attributePaths = {"course", "student"})
    Optional<Review> findByIdempotencyKey(String idempotencyKey);

    boolean existsByCourse_IdAndStudent_Id(UUID courseId, UUID studentId);
}