package com.learningLadder.learningLadder.repository.timeTableRepository;

import com.learningLadder.learningLadder.entity.timeTable.TimeTable;
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

public interface TimeTableRepository extends JpaRepository<TimeTable, UUID>, JpaSpecificationExecutor<TimeTable> {

    @Override
    @EntityGraph(attributePaths = "course")
    @NonNull
    Optional<TimeTable> findById(@NonNull UUID id);

    @Override
    @EntityGraph(attributePaths = "course")
    @NonNull
    Page<TimeTable> findAll(@NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "course")
    @NonNull
    Page<TimeTable> findAll(@NonNull Specification<TimeTable> spec, @NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "course")
    @NonNull
    List<TimeTable> findAll(@NonNull Specification<TimeTable> spec, @NonNull Sort sort);

    @EntityGraph(attributePaths = "course")
    Optional<TimeTable> findByIdempotencyKey(String idempotencyKey);
}