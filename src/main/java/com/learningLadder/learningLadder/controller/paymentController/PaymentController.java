package com.learningLadder.learningLadder.controller.paymentController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
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
import com.learningLadder.learningLadder.enums.paymentStatus.PaymentStatus;
import com.learningLadder.learningLadder.service.paymentService.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/${app.api.version}/payment")
@SecurityRequirement(name = "bearerAuth") // must match the scheme name in your swaggerConfig
@Tag(
        name = "Payment Management Endpoints",
        description = """
                Course checkout, school fees (online or recorded by the bursar) and staff salaries
                (prepare -> approve by a second officer -> confirm payout). Payments are never edited:
                cancel an unfinished payment, or refund a successful one.
                Online-payment endpoints require an Idempotency-Key header so a retry never charges twice."""
)
public class PaymentController {

    /** Keep in sync with PAYMENT_FINANCE_ROLES in SecurityConfig and FINANCE_ROLES in PaymentService. */
    private static final String FINANCE = "hasAnyRole('SUPER_ADMIN', 'ADMIN', 'BURSAR', 'ACCOUNTANT')";

    private static final String IDEMPOTENCY_DESCRIPTION =
            "Unique key per payment attempt (e.g. a UUID generated when the user clicks Pay). "
                    + "Reuse the same key when retrying the same attempt.";

    private final PaymentService paymentService;

    @PostMapping(value = "/checkout", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Pay for the courses in my cart",
            description = """
                    Re-checks prices, availability and the coupon, then starts a Stripe payment.
                    Use clientSecret + publishableKey with Stripe.js (Payment Element) to collect the card.
                    Courses are unlocked when Stripe confirms the payment (webhook), not when the card form closes.
                    A free cart (free courses or a 100% coupon) is completed immediately (freeOfCharge = true).
                    If prices changed since the courses were added, send acceptPriceChanges = true after review.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Checkout started (or completed if free)"),
            @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key, invalid coupon, empty cart or provider not available",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No cart",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Prices changed, course unavailable or key already used",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Payment provider temporarily unavailable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<CheckoutResponseDto>> checkoutCart(
            @Valid @RequestBody CheckoutCartRequestDto request,
            @Parameter(in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_DESCRIPTION)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        CheckoutResponseDto checkout = paymentService.checkoutCart(request, idempotencyKey, currentUserId);

        String message = checkout.freeOfCharge()
                ? "Your courses are unlocked. No payment was needed."
                : checkoutMessage(checkout, "Complete the payment to unlock your courses.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APISuccessResponse.created(message, checkout, currentPath()));
    }

    @PostMapping(value = "/school-fees", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Pay a school fee online",
            description = """
                    A student pays their own fees (a parent or finance staff can pay for a student).
                    Returns the Stripe details to collect the payment. The fee counts as paid only after
                    Stripe confirms it. A fee already paid (same student, year, category, term, installment) is refused.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment started"),
            @ApiResponse(responseCode = "400", description = "Validation failed or missing Idempotency-Key",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not allowed to pay for this student",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Student not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Fee already paid or key already used",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<CheckoutResponseDto>> paySchoolFeeOnline(
            @Valid @RequestBody CreateSchoolFeePaymentRequestDto request,
            @Parameter(in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_DESCRIPTION)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        CheckoutResponseDto checkout = paymentService.paySchoolFeeOnline(
                request, idempotencyKey, currentUserId, authorities(authentication));

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                checkoutMessage(checkout, "Complete the payment to settle the fee."), checkout, currentPath()));
    }

    @PostMapping(value = "/school-fees/record", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(FINANCE)
    @Operation(summary = "Record a school fee paid at the school (bursar)",
            description = "For cash, cheque, bank or mobile-money payments received outside the platform. "
                    + "provider must be MANUAL and an externalReference or receiptNumber is required. "
                    + "The payment is SUCCEEDED immediately; a receipt number is generated if none is given.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment recorded"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not finance staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Fee already paid",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentResponseDto>> recordSchoolFeePayment(
            @Valid @RequestBody CreateSchoolFeePaymentRequestDto request,
            @Parameter(in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_DESCRIPTION)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        PaymentResponseDto payment = paymentService.recordSchoolFeePayment(request, idempotencyKey, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "Fee payment of " + money(payment.amount(), payment.currency()) + " recorded. Receipt "
                        + payment.receiptNumber() + " (" + payment.paymentReference() + ").",
                payment, currentPath()));
    }

    @PostMapping(value = "/salaries", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(FINANCE)
    @Operation(summary = "Prepare a staff salary (step 1 of 3)",
            description = "Creates a PENDING salary. Amount paid = grossAmount + allowancesAmount - deductionsAmount. "
                    + "One salary per staff member and pay period. It must then be approved by ANOTHER finance officer.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Salary prepared"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not finance staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Staff member not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Salary already exists for this period",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentResponseDto>> createSalaryPayment(
            @Valid @RequestBody CreateSalaryPaymentRequestDto request,
            @Parameter(in = ParameterIn.HEADER, required = true, description = IDEMPOTENCY_DESCRIPTION)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        PaymentResponseDto salary = paymentService.createSalaryPayment(request, idempotencyKey, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "Salary " + salary.paymentReference() + " prepared (" + money(salary.amount(), salary.currency())
                        + "). It must now be approved by another finance officer.",
                salary, currentPath()));
    }

    @PatchMapping(value = "/salaries/{id}/approve", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(FINANCE)
    @Operation(summary = "Approve a salary (step 2 of 3)",
            description = "Must be done by a different finance officer than the one who prepared it, "
                    + "and never by the staff member being paid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Salary approved"),
            @ApiResponse(responseCode = "403", description = "Same officer as the preparer, or own salary",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Salary is not pending",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentResponseDto>> approveSalaryPayment(
            @Parameter(description = "Salary payment id") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        PaymentResponseDto salary = paymentService.approveSalaryPayment(id, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Salary " + salary.paymentReference() + " approved. Confirm the payout once the transfer is made.",
                salary, currentPath()));
    }

    @PatchMapping(value = "/salaries/{id}/confirm-payout", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(FINANCE)
    @Operation(summary = "Confirm a salary was paid (step 3 of 3)",
            description = "After the bank or mobile-money transfer: record its reference. The salary becomes SUCCEEDED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Salary marked as paid"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Salary is not approved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentResponseDto>> confirmSalaryPayout(
            @Parameter(description = "Salary payment id") @PathVariable UUID id,
            @Valid @RequestBody ConfirmManualPaymentRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        PaymentResponseDto salary = paymentService.confirmSalaryPayout(id, request, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Salary " + salary.paymentReference() + " of " + money(salary.amount(), salary.currency())
                        + " marked as paid (transfer reference " + salary.externalReference() + ").",
                salary, currentPath()));
    }

    @GetMapping(value = "/mine", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My payments",
            description = "Everything you paid (courses, fees) or received (salary, refunds). "
                    + "Example: paymentType=SCHOOL_FEE&academicYear=2026-2027.")
    @ApiResponse(responseCode = "200", description = "Page of payments")
    public ResponseEntity<APISuccessResponse<PaymentPageResponseDto>> fetchMyPayments(
            @Valid @ParameterObject @ModelAttribute PagedQueryPaymentRequestDto query,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        PaymentPageResponseDto result = paymentService.fetchMyPayments(query, currentUserId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                result.totalElements() == 0 ? "No payments found" : result.totalElements() + " payment(s) found",
                result, currentPath()));
    }

    @GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(FINANCE)
    @Operation(summary = "Search payments (finance)",
            description = """
                    Reports for finance staff. Examples:
                    - fees collected: paymentType=SCHOOL_FEE&academicYear=2026-2027&status=SUCCEEDED
                    - overdue fees: overdueOnly=true
                    - salaries to approve: paymentType=STAFF_SALARY&status=PENDING
                    sortBy: createdAt, paidAt, amount, dueDate, payPeriodStart, status.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching payments"),
            @ApiResponse(responseCode = "403", description = "Not finance staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentPageResponseDto>> searchPayments(
            @Valid @ParameterObject @ModelAttribute PagedQueryPaymentRequestDto query) {

        PaymentPageResponseDto result = paymentService.searchPayments(query);

        return ResponseEntity.ok(APISuccessResponse.ok(
                result.totalElements() == 0 ? "No payments match your search"
                        : "Search completed: " + result.totalElements() + " payment(s) found",
                result, currentPath()));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Fetch a payment",
            description = "Finance staff see every detail; the payer, payee or student concerned see their own view.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The payment"),
            @ApiResponse(responseCode = "403", description = "Not your payment",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentResponseDto>> fetchPayment(
            @Parameter(description = "Payment id") @PathVariable UUID id,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        PaymentResponseDto payment = paymentService.fetchPayment(id, currentUserId, authorities(authentication));

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Payment " + payment.paymentReference() + " is " + payment.status(), payment, currentPath()));
    }

    @PatchMapping(value = "/{id}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel an unfinished payment",
            description = "Payers can cancel their own pending, action-required or failed online payment. "
                    + "Finance staff can also cancel pending or approved salaries. Successful payments cannot be "
                    + "cancelled: refund them instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment cancelled"),
            @ApiResponse(responseCode = "403", description = "Not your payment",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Payment not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Payment already completed, processing or final",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentResponseDto>> cancelPayment(
            @Parameter(description = "Payment id") @PathVariable UUID id,
            @Valid @RequestBody CancelPaymentRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId,
            @Parameter(hidden = true) Authentication authentication) {

        PaymentResponseDto payment = paymentService.cancelPayment(id, request, currentUserId, authorities(authentication));

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Payment " + payment.paymentReference() + " cancelled. No money was taken.", payment, currentPath()));
    }

    @PostMapping(value = "/{id}/refund", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize(FINANCE)
    @Operation(summary = "Refund a payment (finance)",
            description = "Full refund (leave amount empty) or partial refund of a successful course purchase or "
                    + "school fee. Creates a REFUND payment linked to the original. Stripe payments are refunded to "
                    + "the card; manual payments are recorded as refunded by the bursar. A full refund of a course "
                    + "purchase removes access to the courses.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Refund created (see its status)"),
            @ApiResponse(responseCode = "400", description = "Amount too large or payment type not refundable",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Not finance staff",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Payment not successful",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<PaymentResponseDto>> refundPayment(
            @Parameter(description = "Id of the payment to refund") @PathVariable UUID id,
            @Valid @RequestBody RefundPaymentRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "id") UUID currentUserId) {

        PaymentResponseDto refund = paymentService.refundPayment(id, request, currentUserId);

        String amount = money(refund.amount(), refund.currency());
        String message;
        if (refund.status() == PaymentStatus.SUCCEEDED) {
            message = "Refund of " + amount + " completed (" + refund.paymentReference() + ").";
        } else if (refund.status() == PaymentStatus.FAILED) {
            message = "The refund of " + amount + " failed at the payment provider. Please try again or refund manually.";
        } else {
            message = "Refund of " + amount + " is being processed by the payment provider (" + refund.paymentReference() + ").";
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(APISuccessResponse.created(message, refund, currentPath()));
    }

    @PostMapping(value = "/webhook/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Stripe webhook (called by Stripe only)",
            description = """
                    Stripe notifies payment and refund results here. The request body must arrive exactly as Stripe
                    sent it: the Stripe-Signature header is verified against it with the webhook secret, and any
                    forged or modified request is rejected. Configure this URL in the Stripe Dashboard
                    (Developers > Webhooks) or forward it locally with the Stripe CLI.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event received"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid signature",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @Parameter(in = ParameterIn.HEADER, required = true, description = "Signature added by Stripe")
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        paymentService.handleStripeWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    private static String checkoutMessage(CheckoutResponseDto checkout, String nextStep) {
        return "Payment " + checkout.paymentReference() + " of " + money(checkout.amount(), checkout.currency())
                + " started. " + nextStep;
    }

    private static String money(BigDecimal amount, String currency) {
        return (amount == null ? BigDecimal.ZERO : amount).toPlainString() + " " + currency;
    }

    private static List<String> authorities(Authentication authentication) {
        return authentication == null ? List.of()
                : authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static String currentPath() {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
    }
}