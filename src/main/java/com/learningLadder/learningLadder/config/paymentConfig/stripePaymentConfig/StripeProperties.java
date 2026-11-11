package com.learningLadder.learningLadder.config.paymentConfig.stripePaymentConfig;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.integrations.stripe")
public record StripeProperties(

        @NotBlank(message = "app.integrations.stripe.api-key is missing: set STRIPE_API_KEY in your .env")
        @Pattern(regexp = "^(sk|rk)_(test|live)_.+",
                message = "STRIPE_API_KEY must be a secret (sk_...) or restricted (rk_...) key, not a publishable key")
        String apiKey,

        @Pattern(regexp = "^$|^pk_(test|live)_.+", message = "STRIPE_PUBLISHABLE_KEY must start with pk_")
        String publishableKey,

        @Pattern(regexp = "^$|^whsec_.+", message = "STRIPE_WEBHOOK_SECRET must start with whsec_")
        String webhookSecret,

        @NotBlank
        @Pattern(regexp = "^[a-z]{3}$", message = "default-currency must be a lowercase 3-letter ISO code, e.g. usd")
        @DefaultValue("usd")
        String defaultCurrency,

        String successUrl,

        String cancelUrl,

        @Min(0)
        @Max(5)
        @DefaultValue("2")
        int maxNetworkRetries,

        @NotNull
        @DefaultValue("10s")
        Duration connectTimeout,

        @NotNull
        @DefaultValue("30s")
        Duration readTimeout
) {

    public boolean testMode() {
        return apiKey != null && apiKey.contains("_test_");
    }

    public boolean webhookConfigured() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    @Override
    public @NonNull String toString() {
        return "StripeProperties[mode=" + (testMode() ? "TEST" : "LIVE")
                + ", defaultCurrency=" + defaultCurrency
                + ", webhookConfigured=" + webhookConfigured()
                + ", maxNetworkRetries=" + maxNetworkRetries
                + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout
                + ", apiKey=****, webhookSecret=****]";
    }
}