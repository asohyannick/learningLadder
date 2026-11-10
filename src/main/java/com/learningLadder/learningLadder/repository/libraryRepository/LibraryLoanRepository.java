package com.learningLadder.learningLadder.repository.libraryRepository;

import com.learningLadder.learningLadder.entity.library.LibraryLoan;
import com.learningLadder.learningLadder.enums.loanStatus.LoanStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LibraryLoanRepository
        extends JpaRepository<LibraryLoan, UUID>, JpaSpecificationExecutor<LibraryLoan> {

    @Override
    @EntityGraph(attributePaths = "book")
    @NonNull
    Page<LibraryLoan> findAll(@NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "book")
    @NonNull
    Page<LibraryLoan> findAll(@NonNull Specification<LibraryLoan> spec, @NonNull Pageable pageable);

    Optional<LibraryLoan> findByIdempotencyKey(String idempotencyKey);

    long countByStudent_IdAndStatusIn(UUID studentId, Collection<LoanStatus> statuses);

    boolean existsByStudent_IdAndBook_IdAndStatusIn(UUID studentId, UUID bookId, Collection<LoanStatus> statuses);

    boolean existsByStudent_IdAndFeePaidFalseAndLateFeeGreaterThan(UUID studentId, BigDecimal amount);

    boolean existsByBook_Id(UUID bookId);

    long countByStatusIn(Collection<LoanStatus> statuses);

    List<LibraryLoan> findByStatusAndReservationExpiresAtBefore(LoanStatus status, Instant instant);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update versioned libraryLoan l
               set l.status = :overdue
             where l.status = :borrowed
               and l.dueAt < :now
            """)
    int markBorrowedAsOverdue(@Param("borrowed") LoanStatus borrowed,
                              @Param("overdue") LoanStatus overdue,
                              @Param("now") Instant now);
}