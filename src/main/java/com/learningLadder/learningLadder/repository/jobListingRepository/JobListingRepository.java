package com.learningLadder.learningLadder.repository.jobListingRepository;

import com.learningLadder.learningLadder.entity.jobListing.JobListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface JobListingRepository extends JpaRepository<JobListing, UUID>, JpaSpecificationExecutor<JobListing> {

    boolean existsBySlug(String slug);

    @Query(value = "select nextval('job_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();
}