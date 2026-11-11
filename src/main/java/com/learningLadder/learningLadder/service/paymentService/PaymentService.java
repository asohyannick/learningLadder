package com.learningLadder.learningLadder.service.paymentService;

import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.dto.paymentDto.CancelPaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.CheckoutCartRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.CheckoutResponseDto;
import com.learningLadder.learningLadder.dto.paymentDto.ConfirmManualPaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.CreateSalaryPaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.CreateSchoolFeePaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.PagedQueryPaymentRequestDto;
import com.learningLadder.learningLadder.dto.paymentDto.PaymentPageResponseDto;
import com.learningLadder.learningLadder.dto.paymentDto.PaymentResponseDto;
import com.learningLadder.learningLadder.dto.paymentDto.RefundPaymentRequestDto;
import com.learningLadder.learningLadder.entity.coupon.Coupon;
import com.learningLadder.learningLadder.entity.payment.Payment;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCart;
import com.learningLadder.learningLadder.entity.shoppingCart.ShoppingCartItem;
import com.learningLadder.learningLadder.entity.studentProfile.StudentProfile;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.cartStatus.CartStatus;
import com.learningLadder.learningLadder.enums.paymentProvider.PaymentProvider;
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.enums.paymentType.PaymentType;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.forbiddenRequestException.ForbiddenRequestException;
import com.learningLadder.learningLadder.exception.notFoundRequestException.NotFoundRequestException;
import com.learningLadder.learningLadder.mappers.paymentMapper.PaymentMapper;
import com.learningLadder.learningLadder.repository.couponRepository.CouponRepository;
import com.learningLadder.learningLadder.repository.paymentRepository.PaymentRepository;
import com.learningLadder.learningLadder.repository.paymentRepository.PaymentSpecification;
import com.learningLadder.learningLadder.repository.shoppingCartRepository.ShoppingCartRepository;
import com.learningLadder.learningLadder.repository.studentProfileRepository.StudentProfileRepository;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.service.paymentService.StripeGateway.ChargeDetails;
import com.learningLadder.learningLadder.service.paymentService.StripeGateway.IntentResult;
import com.learningLadder.learningLadder.service.paymentService.StripeGateway.RefundResult;
import com.learningLadder.learningLadder.service.shoppingCartService.CouponRules;
import com.learningLadder.learningLadder.service.shoppingCartService.CourseCommerceAdapter;
import com.learningLadder.learningLadder.service.shoppingCartService.CourseCommerceAdapter.CourseSnapshot;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private static final List<CartStatus> OPEN_CART_STATUSES = List.of(CartStatus.ACTIVE, CartStatus.ABANDONED);
    private static final List<PaymentStatus> UNFINISHED = List.of(
            PaymentStatus.PENDING, PaymentStatus.REQUIRES_ACTION, PaymentStatus.PROCESSING);
    private static final List<PaymentStatus> PAID = List.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);
    private static final List<PaymentStatus> NOT_COUNTED = List.of(PaymentStatus.FAILED, PaymentStatus.CANCELLED);

    private static final Set<String> FINANCE_ROLES = Set.of(
            "ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_BURSAR", "ROLE_ACCOUNTANT");
    private static final Set<String> PARENT_ROLES = Set.of("ROLE_PARENT", "ROLE_GUARDIAN");

    private static final List<String> CART_CACHES = List.of(
            CacheNames.SHOPPING_CART_COUNT, CacheNames.SHOPPING_CART_ITEM, CacheNames.SHOPPING_CART_HISTORY,
            CacheNames.SHOPPING_CART_SEARCH, CacheNames.SHOPPING_CART_BY_ID);

    private static final DateTimeFormatter REFERENCE_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int NOTES_MAX_LENGTH = 1000;

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final StripeGateway stripeGateway;
    private final ShoppingCartRepository shoppingCartRepository;
    private final CouponRepository couponRepository;
    private final CouponRules couponRules;
    private final CourseCommerceAdapter courseAdapter;
    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;

    private final SecureRandom random = new SecureRandom();

    public CheckoutResponseDto checkoutCart(CheckoutCartRequestDto dto, String idempotencyKey, UUID userId) {
        String key = requireIdempotencyKey(idempotencyKey);

        Optional<Payment> previous = paymentRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            Payment payment = previous.get();
            if (payment.getPaymentType() != PaymentType.COURSE_PURCHASE || !isPayer(payment, userId)) {
                throw new ConflictRequestException("This Idempotency-Key has already been used");
            }
            return resumeCheckout(payment);
        }
        requireStripe(dto.provider());

        ShoppingCart cart = shoppingCartRepository.findFirstByUser_IdAndStatusIn(userId, OPEN_CART_STATUSES)
                .orElseThrow(() -> new NotFoundRequestException("Your cart is empty"));
        prepareCartForCheckout(cart, dto.acceptPriceChanges());
        if (cart.getItemCount() == 0) {
            throw new BadRequestException("Your cart has no courses to buy");
        }

        Optional<Payment> unfinished = paymentRepository
                .findFirstByShoppingCart_IdAndStatusInOrderByCreatedAtDesc(cart.getId(), UNFINISHED);
        if (unfinished.isPresent()) {
            Payment existing = unfinished.get();
            if (existing.getAmount().compareTo(cart.getTotal()) == 0
                    && existing.getCurrency().equalsIgnoreCase(cart.getCurrency())) {
                return resumeCheckout(existing);
            }
            cancelUnfinished(existing, "Cart changed before payment was completed");
        }

        Payment payment = paymentMapper.toEntity(dto);
        payment.setPayer(cart.getUser());
        payment.setShoppingCart(cart);
        payment.setCurrency(cart.getCurrency());
        payment.setSubtotal(cart.getSubtotal());
        payment.setDiscountAmount(cart.getDiscountTotal());
        payment.setAmount(cart.getTotal());
        payment.setCouponCode(cart.getCouponCode());
        payment.setCouponDiscount(cart.getCouponCode() == null ? null : cart.getCouponDiscount());
        payment.setDescription("Course purchase (" + cart.getItemCount() + " course(s))");
        payment.setPaymentReference(newReference("PAY"));
        payment.setIdempotencyKey(key);

        if (payment.getAmount().signum() == 0) {
            payment.setProvider(PaymentProvider.MANUAL);
            payment.setPaymentMethod(null);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAt(Instant.now());
            payment.setNetAmount(BigDecimal.ZERO);
            Payment saved = saveNew(payment);
            finalizeCoursePurchase(saved);
            log.info("Free checkout {} completed for cart {}", saved.getPaymentReference(), cart.getId());
            return toCheckoutResponse(saved, null, true);
        }

        Payment saved = saveNew(payment);
        IntentResult intent = createIntent(saved, "Learning Ladder - " + saved.getDescription());
        log.info("Checkout {} started for cart {} ({} {})",
                saved.getPaymentReference(), cart.getId(), saved.getAmount(), saved.getCurrency());
        return toCheckoutResponse(saved, intent.clientSecret(), false);
    }

    public CheckoutResponseDto paySchoolFeeOnline(CreateSchoolFeePaymentRequestDto dto, String idempotencyKey,
                                                  UUID userId, Collection<String> authorities) {
        String key = requireIdempotencyKey(idempotencyKey);

        Optional<Payment> previous = paymentRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            Payment payment = previous.get();
            if (payment.getPaymentType() != PaymentType.SCHOOL_FEE || !isPayer(payment, userId)) {
                throw new ConflictRequestException("This Idempotency-Key has already been used");
            }
            return resumeCheckout(payment);
        }
        if (dto.provider() == PaymentProvider.MANUAL) {
            throw new BadRequestException("Manual payments are recorded by the bursar through the record endpoint");
        }
        requireStripe(dto.provider());

        StudentProfile student = findStudent(dto.studentId());
        ensureCanPayFeesFor(student, userId, authorities);
        ensureFeeNotAlreadyPaid(dto, student);

        Payment payment = paymentMapper.toEntity(dto);
        payment.setPayer(userRepository.getReferenceById(userId));
        payment.setStudent(student);
        payment.setDescription(dto.description() != null ? dto.description() : feeDescription(dto));
        payment.setPaymentReference(newReference("FEE"));
        payment.setIdempotencyKey(key);

        Payment saved = saveNew(payment);
        IntentResult intent = createIntent(saved, "Learning Ladder - " + saved.getDescription());
        log.info("Online fee payment {} started for student {} ({} {})",
                saved.getPaymentReference(), student.getId(), saved.getAmount(), saved.getCurrency());
        return toCheckoutResponse(saved, intent.clientSecret(), false);
    }

    public PaymentResponseDto recordSchoolFeePayment(CreateSchoolFeePaymentRequestDto dto, String idempotencyKey,
                                                     UUID staffId) {
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<Payment> previous = paymentRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return paymentMapper.toStaffResponse(previous.get());
        }
        if (dto.provider() != PaymentProvider.MANUAL) {
            throw new BadRequestException("Recorded fee payments must use provider MANUAL");
        }

        StudentProfile student = findStudent(dto.studentId());
        ensureFeeNotAlreadyPaid(dto, student);

        Payment payment = paymentMapper.toEntity(dto);
        payment.setPayer(student.getUser());
        payment.setStudent(student);
        payment.setDescription(dto.description() != null ? dto.description() : feeDescription(dto));
        payment.setStatus(PaymentStatus.SUCCEEDED);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
        }
        if (payment.getReceiptNumber() == null) {
            payment.setReceiptNumber(newReference("RCPT"));
        }
        payment.setNetAmount(payment.getAmount());
        payment.setRecordedBy(staffId);
        payment.setPaymentReference(newReference("FEE"));
        payment.setIdempotencyKey(key);

        Payment saved = saveNew(payment);
        log.info("Manual fee payment {} recorded by {} for student {} ({} {})",
                saved.getPaymentReference(), staffId, student.getId(), saved.getAmount(), saved.getCurrency());
        return paymentMapper.toStaffResponse(saved);
    }

    public PaymentResponseDto createSalaryPayment(CreateSalaryPaymentRequestDto dto, String idempotencyKey, UUID staffId) {
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<Payment> previous = paymentRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            return paymentMapper.toStaffResponse(previous.get());
        }

        User payee = userRepository.findById(dto.payeeUserId())
                .orElseThrow(() -> new NotFoundRequestException("Staff member not found with id " + dto.payeeUserId()));

        if (paymentRepository.existsByPaymentTypeAndPayee_IdAndPayPeriodStartAndPayPeriodEndAndStatusNotIn(
                PaymentType.STAFF_SALARY, payee.getId(), dto.payPeriodStart(), dto.payPeriodEnd(), NOT_COUNTED)) {
            throw new ConflictRequestException("A salary for this staff member and pay period already exists");
        }

        Payment payment = paymentMapper.toEntity(dto);
        payment.setPayee(payee);
        payment.setRecordedBy(staffId);
        payment.setDescription(dto.description() != null ? dto.description()
                : "Salary " + dto.payPeriodStart() + " to " + dto.payPeriodEnd());
        payment.setPaymentReference(newReference("SAL"));
        payment.setIdempotencyKey(key);

        Payment saved = saveNew(payment);
        log.info("Salary {} prepared by {} for {} ({} {})",
                saved.getPaymentReference(), staffId, payee.getId(), saved.getAmount(), saved.getCurrency());
        return paymentMapper.toStaffResponse(saved);
    }

    public PaymentResponseDto approveSalaryPayment(UUID paymentId, UUID approverId) {
        Payment payment = findPayment(paymentId);
        requireType(payment, PaymentType.STAFF_SALARY);

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new ConflictRequestException("Only pending salaries can be approved. This one is " + payment.getStatus());
        }
        if (approverId.equals(payment.getRecordedBy())) {
            throw new ForbiddenRequestException("A salary must be approved by a different finance officer than the one who prepared it");
        }
        if (payment.getPayee() != null && approverId.equals(payment.getPayee().getId())) {
            throw new ForbiddenRequestException("You cannot approve your own salary");
        }

        payment.setStatus(PaymentStatus.APPROVED);
        payment.setApprovedBy(approverId);
        payment.setApprovedAt(Instant.now());
        log.info("Salary {} approved by {}", payment.getPaymentReference(), approverId);
        return paymentMapper.toStaffResponse(save(payment));
    }

    public PaymentResponseDto confirmSalaryPayout(UUID paymentId, ConfirmManualPaymentRequestDto dto, UUID staffId) {
        Payment payment = findPayment(paymentId);
        requireType(payment, PaymentType.STAFF_SALARY);

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new ConflictRequestException("Only approved salaries can be marked as paid. This one is " + payment.getStatus());
        }

        paymentMapper.applyManualConfirmation(dto, payment);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
        }
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setNetAmount(payment.getAmount());
        appendNote(payment, dto.notes());
        appendNote(payment, "Payout confirmed by " + staffId);

        log.info("Salary {} paid out (reference {})", payment.getPaymentReference(), payment.getExternalReference());
        return paymentMapper.toStaffResponse(save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponseDto fetchPayment(UUID paymentId, UUID userId, Collection<String> authorities) {
        Payment payment = findPayment(paymentId);
        if (isFinance(authorities)) {
            return paymentMapper.toStaffResponse(payment);
        }
        if (isPayer(payment, userId) || isPayee(payment, userId) || isStudentConcerned(payment, userId)) {
            return paymentMapper.toUserResponse(payment);
        }
        throw new ForbiddenRequestException("You can only view your own payments");
    }

    @Transactional(readOnly = true)
    public PaymentPageResponseDto fetchMyPayments(PagedQueryPaymentRequestDto query, UUID userId) {
        return search(query.forUser(userId));
    }

    @Transactional(readOnly = true)
    public PaymentPageResponseDto searchPayments(PagedQueryPaymentRequestDto query) {
        return search(query);
    }

    public PaymentResponseDto cancelPayment(UUID paymentId, CancelPaymentRequestDto dto, UUID userId,
                                            Collection<String> authorities) {
        Payment payment = findPayment(paymentId);
        boolean finance = isFinance(authorities);
        if (!finance && !isPayer(payment, userId)) {
            throw new ForbiddenRequestException("You can only cancel your own payments");
        }

        boolean cancellable = payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.REQUIRES_ACTION
                || payment.getStatus() == PaymentStatus.FAILED
                || (finance && payment.getStatus() == PaymentStatus.APPROVED);
        if (!cancellable) {
            throw new ConflictRequestException("This payment cannot be cancelled because it is " + payment.getStatus());
        }

        cancelUnfinished(payment, dto.reason());
        log.info("Payment {} cancelled by {}: {}", payment.getPaymentReference(), userId, dto.reason());
        return finance ? paymentMapper.toStaffResponse(save(payment)) : paymentMapper.toUserResponse(save(payment));
    }

    public PaymentResponseDto refundPayment(UUID paymentId, RefundPaymentRequestDto dto, UUID staffId) {
        Payment original = findPayment(paymentId);

        BigDecimal requested = getBigDecimal(dto, original);

        Payment refund = paymentMapper.toRefundEntity(dto);
        refund.setOriginalPayment(original);
        refund.setPayee(original.getPayer());
        refund.setStudent(original.getStudent());
        refund.setCurrency(original.getCurrency());
        refund.setSubtotal(requested);
        refund.setAmount(requested);
        refund.setProvider(original.getProvider());
        refund.setPaymentMethod(original.getPaymentMethod());
        refund.setRecordedBy(staffId);
        refund.setDescription("Refund of " + original.getPaymentReference());
        refund.setPaymentReference(newReference("REF"));
        refund.setIdempotencyKey("refund-" + UUID.randomUUID());

        if (original.getProvider() == PaymentProvider.STRIPE && original.getProviderPaymentId() != null) {
            long minor = stripeGateway.toMinorUnits(requested, original.getCurrency());
            refund.setAmountInMinorUnits(minor);
            RefundResult result = stripeGateway.createRefund(original.getProviderPaymentId(), minor, refund.getIdempotencyKey());
            refund.setProviderTransferId(result.id());
            refund.setProviderStatus(result.status());
            switch (result.status()) {
                case "succeeded" -> markRefundSucceeded(refund);
                case "failed", "canceled" -> {
                    refund.setStatus(PaymentStatus.FAILED);
                    refund.setFailedAt(Instant.now());
                }
                default -> refund.setStatus(PaymentStatus.PROCESSING);
            }
        } else {
            markRefundSucceeded(refund);
        }

        Payment saved = saveNew(refund);
        log.info("Refund {} of {} {} for payment {} ({})", saved.getPaymentReference(), requested,
                original.getCurrency(), original.getPaymentReference(), saved.getStatus());
        return paymentMapper.toStaffResponse(saved);
    }

    private static @NonNull BigDecimal getBigDecimal(RefundPaymentRequestDto dto, Payment original) {
        if (original.getPaymentType() == PaymentType.REFUND || original.getPaymentType() == PaymentType.STAFF_SALARY) {
            throw new BadRequestException("Only course purchases and school fees can be refunded");
        }
        if (original.getStatus() != PaymentStatus.SUCCEEDED && original.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new ConflictRequestException("Only successful payments can be refunded. This one is " + original.getStatus());
        }

        BigDecimal refundable = original.getAmount().subtract(original.getRefundedAmount());
        BigDecimal requested = dto.amount() == null ? refundable : dto.amount();
        if (requested.signum() <= 0 || requested.compareTo(refundable) > 0) {
            throw new BadRequestException("You can refund at most " + refundable.toPlainString() + " " + original.getCurrency());
        }
        return requested;
    }

    public void handleStripeWebhook(String payload, String signatureHeader) {
        Event event = stripeGateway.verifyWebhook(payload, signatureHeader);
        StripeObject object;

        switch (event.getType()) {
            case "payment_intent.succeeded", "payment_intent.processing", "payment_intent.requires_action",
                 "payment_intent.payment_failed", "payment_intent.canceled" -> {
                object = stripeGateway.eventObject(event);
                if (object instanceof PaymentIntent intent) {
                    onPaymentIntentUpdate(intent);
                }
            }
            case "refund.created", "refund.updated", "refund.failed" -> {
                object = stripeGateway.eventObject(event);
                if (object instanceof Refund refund) {
                    onRefundUpdate(refund);
                }
            }
            default -> log.debug("Ignoring Stripe event {} ({})", event.getId(), event.getType());
        }
    }

    private void onPaymentIntentUpdate(PaymentIntent intent) {
        Optional<Payment> found = paymentRepository.findByProviderPaymentId(intent.getId());
        if (found.isEmpty()) {
            log.warn("Stripe PaymentIntent {} does not match any payment", intent.getId());
            return;
        }
        Payment payment = found.get();
        payment.setProviderStatus(intent.getStatus());

        if (PAID.contains(payment.getStatus())) {
            return;
        }

        switch (intent.getStatus()) {
            case "succeeded" -> {
                if (payment.getAmountInMinorUnits() != null && intent.getAmountReceived() != null
                        && !payment.getAmountInMinorUnits().equals(intent.getAmountReceived())) {
                    appendNote(payment, "REVIEW: Stripe received " + intent.getAmountReceived()
                            + " minor units, expected " + payment.getAmountInMinorUnits());
                    log.error("Amount mismatch on payment {}", payment.getPaymentReference());
                }
                payment.setStatus(PaymentStatus.SUCCEEDED);
                payment.setPaidAt(Instant.now());
                payment.setFailureCode(null);
                payment.setFailureMessage(null);
                applyChargeDetails(payment, intent.getLatestCharge());
                if (payment.getPaymentType() == PaymentType.COURSE_PURCHASE) {
                    finalizeCoursePurchase(payment);
                }
                log.info("Payment {} succeeded ({} {})", payment.getPaymentReference(), payment.getAmount(), payment.getCurrency());
            }
            case "processing" -> payment.setStatus(PaymentStatus.PROCESSING);
            case "requires_action" -> payment.setStatus(PaymentStatus.REQUIRES_ACTION);
            case "requires_payment_method" -> {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailedAt(Instant.now());
                if (intent.getLastPaymentError() != null) {
                    payment.setFailureCode(intent.getLastPaymentError().getCode());
                    payment.setFailureMessage(truncate(intent.getLastPaymentError().getMessage(), 500));
                }
                log.info("Payment {} failed: {}", payment.getPaymentReference(), payment.getFailureCode());
            }
            case "canceled" -> {
                payment.setStatus(PaymentStatus.CANCELLED);
                payment.setCancelledAt(Instant.now());
            }
            default -> log.debug("PaymentIntent {} status {}", intent.getId(), intent.getStatus());
        }
        save(payment);
    }

    private void onRefundUpdate(Refund stripeRefund) {
        Optional<Payment> found = paymentRepository.findByProviderTransferId(stripeRefund.getId());
        if (found.isEmpty()) {
            return;
        }
        Payment refund = found.get();
        refund.setProviderStatus(stripeRefund.getStatus());
        if (refund.getStatus() == PaymentStatus.SUCCEEDED || refund.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        switch (stripeRefund.getStatus()) {
            case "succeeded" -> markRefundSucceeded(refund);
            case "failed", "canceled" -> {
                refund.setStatus(PaymentStatus.FAILED);
                refund.setFailedAt(Instant.now());
                refund.setFailureMessage(truncate(stripeRefund.getFailureReason(), 500));
                log.error("Refund {} failed: {}", refund.getPaymentReference(), stripeRefund.getFailureReason());
            }
            default -> refund.setStatus(PaymentStatus.PROCESSING);
        }
        save(refund);
    }

    private void prepareCartForCheckout(ShoppingCart cart, boolean acceptPriceChanges) {
        boolean pricesChanged = false;
        for (ShoppingCartItem item : cart.getItems()) {
            CourseSnapshot current = courseAdapter.snapshotOf(item.getCourse());
            if (!item.isSavedForLater() && !current.purchasable()) {
                throw new ConflictRequestException("\"" + item.getCourseTitle()
                        + "\" is no longer available. Remove it from your cart to continue.");
            }
            boolean differs = current.price().compareTo(item.getUnitPrice()) != 0
                    || current.discount().compareTo(item.getDiscountAmount()) != 0;
            if (differs) {
                if (!item.isSavedForLater()) {
                    pricesChanged = true;
                }
                item.setUnitPrice(current.price());
                item.setDiscountAmount(current.discount().min(current.price()));
            }
            item.setPriceChanged(false);
        }
        if (pricesChanged && !acceptPriceChanges) {
            throw new ConflictRequestException("Some course prices have changed. Please review your cart "
                    + "and confirm with acceptPriceChanges = true.");
        }

        Coupon coupon = null;
        if (cart.getCouponCode() != null) {
            coupon = couponRepository.findByCode(cart.getCouponCode())
                    .orElseThrow(() -> new BadRequestException("Your coupon is no longer valid. Remove it to continue."));
            Optional<String> problem = couponRules.findProblem(coupon, cart, cart.getUser().getId(), Instant.now());
            if (problem.isPresent()) {
                throw new BadRequestException(problem.get() + " Remove the coupon to continue.");
            }
        }
        couponRules.recalculateTotals(cart, coupon);
    }

    private void finalizeCoursePurchase(Payment payment) {
        ShoppingCart cart = payment.getShoppingCart();
        if (cart == null || cart.getStatus() == CartStatus.CHECKED_OUT) {
            return;
        }
        if (cart.getTotal().compareTo(payment.getAmount()) != 0
                || !cart.getCurrency().equalsIgnoreCase(payment.getCurrency())) {
            appendNote(payment, "REVIEW: cart changed after payment (cart total " + cart.getTotal()
                    + " " + cart.getCurrency() + "). Courses not unlocked automatically.");
            log.error("Payment {} succeeded but cart {} changed; manual review needed",
                    payment.getPaymentReference(), cart.getId());
            return;
        }

        List<UUID> courseIds = cart.getItems().stream()
                .filter(item -> !item.isSavedForLater())
                .map(item -> item.getCourse().getId())
                .toList();

        cart.setStatus(CartStatus.CHECKED_OUT);
        cart.setCheckedOutAt(Instant.now());
        cart.setCheckoutReference(payment.getPaymentReference());

        if (cart.getCouponCode() != null) {
            couponRepository.findByCode(cart.getCouponCode())
                    .ifPresent(coupon -> coupon.setRedemptionCount(coupon.getRedemptionCount() + 1));
        }

        courseAdapter.grantCourseAccess(payment.getPayer().getId(), courseIds);
        clearCartCaches();
        log.info("Cart {} checked out with payment {} ({} course(s) unlocked)",
                cart.getId(), payment.getPaymentReference(), courseIds.size());
    }

    private void ensureCanPayFeesFor(StudentProfile student, UUID userId, Collection<String> authorities) {
        if (isFinance(authorities) || hasAny(authorities, PARENT_ROLES)) {
            return;
        }
        if (student.getUser() != null && userId.equals(student.getUser().getId())) {
            return;
        }
        throw new ForbiddenRequestException("You can only pay fees for your own student profile");
    }

    private void ensureFeeNotAlreadyPaid(CreateSchoolFeePaymentRequestDto dto, StudentProfile student) {
        boolean alreadyPaid = paymentRepository
                .findByPaymentTypeAndStudent_IdAndAcademicYearAndFeeCategoryAndStatusIn(
                        PaymentType.SCHOOL_FEE, student.getId(), dto.academicYear(), dto.feeCategory(),
                        List.of(PaymentStatus.SUCCEEDED, PaymentStatus.PROCESSING))
                .stream()
                .anyMatch(p -> Objects.equals(p.getTerm(), dto.term())
                        && Objects.equals(p.getInstallmentNumber(), dto.installmentNumber()));
        if (alreadyPaid) {
            throw new ConflictRequestException("This fee (" + feeDescription(dto) + ") has already been paid for this student");
        }
    }

    private String feeDescription(CreateSchoolFeePaymentRequestDto dto) {
        StringBuilder text = new StringBuilder("School fee: ").append(dto.feeCategory()).append(' ').append(dto.academicYear());
        if (dto.term() != null) text.append(' ').append(dto.term());
        if (dto.installmentNumber() != null) {
            text.append(" (installment ").append(dto.installmentNumber());
            if (dto.totalInstallments() != null) text.append('/').append(dto.totalInstallments());
            text.append(')');
        }
        return text.toString();
    }

    private IntentResult createIntent(Payment payment, String description) {
        long minor = stripeGateway.toMinorUnits(payment.getAmount(), payment.getCurrency());
        IntentResult intent = stripeGateway.createPaymentIntent(minor, payment.getCurrency(), description,
                payment.getIdempotencyKey() + ":intent",
                Map.of("paymentId", payment.getId().toString(),
                        "paymentReference", payment.getPaymentReference(),
                        "paymentType", payment.getPaymentType().name()));
        payment.setAmountInMinorUnits(minor);
        payment.setProviderPaymentId(intent.id());
        payment.setProviderStatus(intent.status());
        save(payment);
        return intent;
    }

    private CheckoutResponseDto resumeCheckout(Payment payment) {
        if (UNFINISHED.contains(payment.getStatus()) || payment.getStatus() == PaymentStatus.FAILED) {
            if (payment.getProvider() == PaymentProvider.STRIPE && payment.getProviderPaymentId() != null) {
                IntentResult intent = stripeGateway.retrievePaymentIntent(payment.getProviderPaymentId());
                return toCheckoutResponse(payment, intent.clientSecret(), false);
            }
        }
        return toCheckoutResponse(payment, null, payment.getAmount().signum() == 0);
    }

    private void cancelUnfinished(Payment payment, String reason) {
        if (payment.getProvider() == PaymentProvider.STRIPE && payment.getProviderPaymentId() != null) {
            stripeGateway.cancelPaymentIntentQuietly(payment.getProviderPaymentId());
        }
        payment.setStatus(PaymentStatus.CANCELLED);
        payment.setCancelledAt(Instant.now());
        appendNote(payment, "Cancelled: " + reason);
        save(payment);
    }

    private void markRefundSucceeded(Payment refund) {
        refund.setStatus(PaymentStatus.SUCCEEDED);
        refund.setPaidAt(Instant.now());

        Payment original = refund.getOriginalPayment();
        BigDecimal refunded = original.getRefundedAmount().add(refund.getAmount());
        original.setRefundedAmount(refunded);
        original.setRefundedAt(Instant.now());
        boolean full = refunded.compareTo(original.getAmount()) >= 0;
        original.setStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);

        if (full && original.getPaymentType() == PaymentType.COURSE_PURCHASE && original.getShoppingCart() != null) {
            List<UUID> courseIds = original.getShoppingCart().getItems().stream()
                    .filter(item -> !item.isSavedForLater())
                    .map(item -> item.getCourse().getId())
                    .toList();
            courseAdapter.revokeCourseAccess(original.getPayer().getId(), courseIds);
        }
    }

    private void applyChargeDetails(Payment payment, String chargeId) {
        ChargeDetails details = stripeGateway.fetchChargeDetails(chargeId);
        payment.setReceiptUrl(details.receiptUrl());
        payment.setCardBrand(details.cardBrand());
        payment.setCardLast4(details.cardLast4());
        if (details.feeMinorUnits() != null) {
            BigDecimal fee = stripeGateway.fromMinorUnits(details.feeMinorUnits(), payment.getCurrency());
            payment.setProviderFee(fee);
            payment.setNetAmount(payment.getAmount().subtract(fee));
        } else {
            payment.setNetAmount(payment.getAmount());
        }
    }

    private CheckoutResponseDto toCheckoutResponse(Payment payment, String clientSecret, boolean free) {
        return new CheckoutResponseDto(
                payment.getId(),
                payment.getPaymentReference(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getAmount().setScale(2, RoundingMode.HALF_UP),
                payment.getCurrency(),
                clientSecret,
                clientSecret == null ? null : stripeGateway.publishableKey(),
                null,
                null,
                free);
    }

    private void requireStripe(PaymentProvider provider) {
        if (provider != PaymentProvider.STRIPE) {
            throw new BadRequestException("Only Stripe is available for online payments at the moment");
        }
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new BadRequestException("The Idempotency-Key header is required (max 100 characters)");
        }
        return idempotencyKey.trim();
    }

    private String newReference(String prefix) {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(REFERENCE_ALPHABET.charAt(random.nextInt(REFERENCE_ALPHABET.length())));
        }
        return prefix + "-" + REFERENCE_DATE.format(Instant.now()) + "-" + code;
    }

    private void appendNote(Payment payment, String note) {
        if (note == null || note.isBlank()) {
            return;
        }
        String entry = "[" + Instant.now() + "] " + note.trim();
        String combined = payment.getNotes() == null ? entry : payment.getNotes() + "\n" + entry;
        payment.setNotes(combined.length() > NOTES_MAX_LENGTH
                ? combined.substring(combined.length() - NOTES_MAX_LENGTH) : combined);
    }

    private Payment saveNew(Payment payment) {
        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (BadRequestException ex) {
            throw new ConflictRequestException("This payment already exists (duplicate request, cart already paid, "
                    + "or salary already prepared for this period)");
        }
    }

    private Payment save(Payment payment) {
        try {
            return paymentRepository.saveAndFlush(payment);
        } catch (BadRequestException ex) {
            throw new ConflictRequestException("This payment was updated at the same time. Please reload and try again.");
        }
    }

    private PaymentPageResponseDto search(PagedQueryPaymentRequestDto query) {
        Page<Payment> page = paymentRepository.findAll(PaymentSpecification.fromQuery(query), query.toPageable());
        return new PaymentPageResponseDto(paymentMapper.toSummaryList(page.getContent()),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private Payment findPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Payment not found with id " + id));
    }

    private StudentProfile findStudent(UUID id) {
        return studentProfileRepository.findById(id)
                .orElseThrow(() -> new NotFoundRequestException("Student not found with id " + id));
    }

    private void requireType(Payment payment, PaymentType type) {
        if (payment.getPaymentType() != type) {
            throw new BadRequestException("This action only applies to " + type + " payments");
        }
    }

    private boolean isPayer(Payment payment, UUID userId) {
        return payment.getPayer() != null && payment.getPayer().getId().equals(userId);
    }

    private boolean isPayee(Payment payment, UUID userId) {
        return payment.getPayee() != null && payment.getPayee().getId().equals(userId);
    }

    private boolean isStudentConcerned(Payment payment, UUID userId) {
        return payment.getStudent() != null && payment.getStudent().getUser() != null
                && payment.getStudent().getUser().getId().equals(userId);
    }

    private boolean isFinance(Collection<String> authorities) {
        return hasAny(authorities, FINANCE_ROLES);
    }

    private static boolean hasAny(Collection<String> authorities, Set<String> roles) {
        return authorities != null && authorities.stream().anyMatch(roles::contains);
    }

    private void clearCartCaches() {
        CART_CACHES.forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}