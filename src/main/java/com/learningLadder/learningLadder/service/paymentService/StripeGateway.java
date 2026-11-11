package com.learningLadder.learningLadder.service.paymentService;

import com.learningLadder.learningLadder.config.paymentConfig.stripePaymentConfig.StripeProperties;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.stripe.StripeClient;
import com.stripe.exception.CardException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class StripeGateway {

    private static final Set<String> ZERO_DECIMAL = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF",
            "UGX", "VND", "VUV", "XAF", "XOF", "XPF");
    private static final Set<String> THREE_DECIMAL = Set.of("BHD", "JOD", "KWD", "OMR", "TND");

    private final StripeClient stripeClient;
    private final StripeProperties properties;

    public record IntentResult(String id, String clientSecret, String status) {
    }

    public record RefundResult(String id, String status) {
    }

    public record ChargeDetails(String receiptUrl, String cardBrand, String cardLast4, Long feeMinorUnits) {
    }

    /** Creates a PaymentIntent. The idempotency key makes a retried call return the same intent. */
    public IntentResult createPaymentIntent(long amountMinorUnits, String currency, String description,
                                            String idempotencyKey, Map<String, String> metadata) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountMinorUnits)
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .setDescription(description)
                .putAllMetadata(metadata)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build())
                .build();
        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().create(params, options(idempotencyKey));
            return new IntentResult(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException ex) {
            throw translate(ex, "create payment");
        }
    }

    public IntentResult retrievePaymentIntent(String intentId) {
        try {
            PaymentIntent intent = stripeClient.v1().paymentIntents().retrieve(intentId);
            return new IntentResult(intent.getId(), intent.getClientSecret(), intent.getStatus());
        } catch (StripeException ex) {
            throw translate(ex, "retrieve payment");
        }
    }

    public void cancelPaymentIntentQuietly(String intentId) {
        try {
            stripeClient.v1().paymentIntents().cancel(intentId);
        } catch (StripeException ex) {
            log.warn("Could not cancel Stripe PaymentIntent {}: {}", intentId, ex.getMessage());
        }
    }

    public RefundResult createRefund(String paymentIntentId, long amountMinorUnits, String idempotencyKey) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(amountMinorUnits)
                .build();
        try {
            Refund refund = stripeClient.v1().refunds().create(params, options(idempotencyKey));
            return new RefundResult(refund.getId(), refund.getStatus());
        } catch (StripeException ex) {
            throw translate(ex, "refund payment");
        }
    }

    public ChargeDetails fetchChargeDetails(String chargeId) {
        if (chargeId == null) {
            return new ChargeDetails(null, null, null, null);
        }
        try {
            Charge charge = stripeClient.v1().charges().retrieve(chargeId);
            String brand = null;
            String last4 = null;
            if (charge.getPaymentMethodDetails() != null && charge.getPaymentMethodDetails().getCard() != null) {
                brand = charge.getPaymentMethodDetails().getCard().getBrand();
                last4 = charge.getPaymentMethodDetails().getCard().getLast4();
            }
            Long fee = null;
            if (charge.getBalanceTransaction() != null) {
                BalanceTransaction transaction = stripeClient.v1().balanceTransactions().retrieve(charge.getBalanceTransaction());
                fee = transaction.getFee();
            }
            return new ChargeDetails(charge.getReceiptUrl(), brand, last4, fee);
        } catch (StripeException ex) {
            log.warn("Could not load Stripe charge {} details: {}", chargeId, ex.getMessage());
            return new ChargeDetails(null, null, null, null);
        }
    }

    public Event verifyWebhook(String payload, String signatureHeader) {
        if (!properties.webhookConfigured()) {
            log.error("Stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new BadRequestException("Missing Stripe-Signature header");
        }
        try {
            return Webhook.constructEvent(payload, signatureHeader, properties.webhookSecret());
        } catch (SignatureVerificationException ex) {
            log.warn("Rejected Stripe webhook with an invalid signature");
            throw new BadRequestException("Invalid Stripe signature");
        }
    }

    public StripeObject eventObject(Event event) {
        return event.getDataObjectDeserializer().getObject().orElseGet(() -> {
            try {
                return event.getDataObjectDeserializer().deserializeUnsafe();
            } catch (EventDataObjectDeserializationException ex) {
                throw new BadRequestException("Could not read Stripe event " + event.getId());
            }
        });
    }


    public long toMinorUnits(BigDecimal amount, String currency) {
        String code = currency.toUpperCase(Locale.ROOT);
        if (ZERO_DECIMAL.contains(code)) {
            return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        if (THREE_DECIMAL.contains(code)) {
            long thousandths = amount.movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact();
            return Math.round(thousandths / 10.0) * 10; // Stripe requires the last digit to be 0
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public BigDecimal fromMinorUnits(long minorUnits, String currency) {
        String code = currency.toUpperCase(Locale.ROOT);
        if (ZERO_DECIMAL.contains(code)) {
            return BigDecimal.valueOf(minorUnits).setScale(2, RoundingMode.UNNECESSARY);
        }
        int scale = THREE_DECIMAL.contains(code) ? 3 : 2;
        return BigDecimal.valueOf(minorUnits, scale).setScale(2, RoundingMode.HALF_UP);
    }

    public String publishableKey() {
        return properties.publishableKey();
    }


    private RequestOptions options(String idempotencyKey) {
        return RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
    }

    private RuntimeException translate(StripeException ex, String action) {
        if (ex instanceof CardException card) {
            return new BadRequestException(card.getUserMessage() != null ? card.getUserMessage() : "Your card was declined.");
        }
        if (ex instanceof InvalidRequestException) {
            log.warn("Stripe rejected request to {}: {}", action, ex.getMessage());
            String reason = ex.getStripeError() != null && ex.getStripeError().getMessage() != null
                    ? ex.getStripeError().getMessage() : "invalid payment request";
            return new BadRequestException("The payment could not be created: " + reason);
        }
        log.error("Stripe error while trying to {} (request id {}): {}", action, ex.getRequestId(), ex.getMessage());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The payment service is temporarily unavailable. Please try again in a moment.");
    }
}