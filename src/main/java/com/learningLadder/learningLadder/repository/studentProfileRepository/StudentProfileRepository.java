package com.learningLadder.learningLadder.repository.studentProfileRepository;

import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository
        extends JpaRepository<StudentProfile, UUID>, JpaSpecificationExecutor<StudentProfile> {

    Optional<StudentProfile> findByIdempotencyKey(String idempotencyKey);

    boolean existsByUserId(UUID userId);

    @Override
    @EntityGraph(attributePaths = {"profilePicture"})
    @NonNull
    Page<StudentProfile> findAll(@NonNull Specification<StudentProfile> spec, @NonNull Pageable pageable);

    Optional<StudentProfile> findByUserId(UUID userId);

}