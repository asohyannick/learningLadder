package com.learningLadder.learningLadder.repository.tutorProfileRepository;

import com.learningLadder.learningLadder.entity.tutorProfile.TutorProfile;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface TutorProfileRepository
        extends JpaRepository<TutorProfile, UUID>, JpaSpecificationExecutor<TutorProfile> {

    Optional<TutorProfile> findByIdempotencyKey(String idempotencyKey);

    boolean existsByUserId(UUID userId);

    @Override
    @EntityGraph(attributePaths = {"profilePicture"})
    @NonNull
    Page<TutorProfile> findAll(@NonNull Specification<TutorProfile> spec, @NonNull Pageable pageable);
}