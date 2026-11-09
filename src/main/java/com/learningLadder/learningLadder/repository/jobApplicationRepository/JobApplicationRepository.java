package com.learningLadder.learningLadder.repository.jobApplicationRepository;

import com.learningLadder.learningLadder.entity.jobApplication.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, UUID>, JpaSpecificationExecutor<JobApplication> {

    Optional<JobApplication> findByIdempotencyKey(String idempotencyKey);

    boolean existsByApplicantId(UUID applicantId);
}