package com.learningLadder.learningLadder.entity.payment;

import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCart;
import com.learningLadder.learningLadder.entity.storedFile.StoredFile;
import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.paymentDirection.PaymentDirection;
import com.learningLadder.learningLadder.enums.paymentMethod.PaymentMethod;
import com.learningLadder.learningLadder.enums.paymentProvider.PaymentProvider;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.paymentType.PaymentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity(name = "payments")
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_reference", columnNames = {"payment_reference"}),
                @UniqueConstraint(name = "uk_payment_idempotency_key", columnNames = {"idempotency_key"}),
                @UniqueConstraint(name = "uk_payment_provider_payment_id", columnNames = {"provider_payment_id"})
        },
        indexes = {
                @Index(name = "idx_payment_type_status", columnList = "payment_type, status"),
                @Index(name = "idx_payment_payer", columnList = "payer_id"),
                @Index(name = "idx_payment_payee", columnList = "payee_id"),
                @Index(name = "idx_payment_student", columnList = "student_id"),
                @Index(name = "idx_payment_cart", columnList = "shopping_cart_id"),
                @Index(name = "idx_payment_paid_at", columnList = "paid_at"),
                @Index(name = "idx_payment_fee_period", columnList = "academic_year, term"),
                @Index(name = "idx_payment_salary_period", columnList = "pay_period_start, pay_period_end"),
                @Index(name = "idx_payment_checkout_session", columnList = "provider_checkout_session_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_reference", nullable = false, updatable = false, length = 30)
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, updatable = false, length = 30)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false, length = 20)
    private PaymentDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_id")
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_id")
    private User payee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id")
    private StudentProfile student;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_in_minor_units")
    private Long amountInMinorUnits;

    @Column(name = "provider_fee", precision = 12, scale = 2)
    private BigDecimal providerFee;

    @Column(name = "net_amount", precision = 12, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopping_cart_id")
    private ShoppingCart shoppingCart;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "coupon_discount", precision = 12, scale = 2)
    private BigDecimal couponDiscount;

    @Column(name = "academic_year", length = 20)
    private String academicYear;

    @Column(name = "term", length = 30)
    private String term;

    @Column(name = "fee_category", length = 50)
    private String feeCategory;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "total_installments")
    private Integer totalInstallments;

    @Column(name = "pay_period_start")
    private LocalDate payPeriodStart;

    @Column(name = "pay_period_end")
    private LocalDate payPeriodEnd;

    @Column(name = "gross_amount", precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "deductions_amount", precision = 12, scale = 2)
    private BigDecimal deductionsAmount;

    @Column(name = "allowances_amount", precision = 12, scale = 2)
    private BigDecimal allowancesAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payslip_document_id")
    private StoredFile payslipDocument;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_id")
    private Payment originalPayment;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "provider_payment_id", length = 100)
    private String providerPaymentId;

    @Column(name = "provider_checkout_session_id", length = 100)
    private String providerCheckoutSessionId;

    @Column(name = "provider_customer_id", length = 100)
    private String providerCustomerId;

    @Column(name = "provider_transfer_id", length = 100)
    private String providerTransferId;

    @Column(name = "provider_status", length = 50)
    private String providerStatus;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "receipt_url", length = 1024)
    private String receiptUrl;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_document_id")
    private StoredFile receiptDocument;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}