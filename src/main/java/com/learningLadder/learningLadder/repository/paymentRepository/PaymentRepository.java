package com.learningLadder.learningLadder.repository.paymentRepository;

import com.learningLadder.learningLadder.entity.payment.Payment;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.paymentType.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Optional<Payment> findByProviderTransferId(String providerTransferId);

    Optional<Payment> findFirstByShoppingCart_IdAndStatusInOrderByCreatedAtDesc(UUID cartId, Collection<PaymentStatus> statuses);

    boolean existsByPaymentTypeAndPayee_IdAndPayPeriodStartAndPayPeriodEndAndStatusNotIn(
            PaymentType type, UUID payeeId, LocalDate start, LocalDate end, Collection<PaymentStatus> excluded);

    List<Payment> findByPaymentTypeAndStudent_IdAndAcademicYearAndFeeCategoryAndStatusIn(
            PaymentType type, UUID studentId, String academicYear, String feeCategory, Collection<PaymentStatus> statuses);
}