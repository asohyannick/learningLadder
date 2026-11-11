package com.learningLadder.learningLadder.config.paymentConfig.stripePaymentConfig;

import com.stripe.StripeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripePaymentConfig {

    @Bean
    public StripeClient stripeClient(StripeProperties properties) {
        StripeClient client = StripeClient.builder()
                .setApiKey(properties.apiKey())
                .setMaxNetworkRetries(properties.maxNetworkRetries())
                .setConnectTimeout((int) properties.connectTimeout().toMillis())
                .setReadTimeout((int) properties.readTimeout().toMillis())
                .build();

        log.info("Stripe client configured: {}", properties);

        if (!properties.testMode()) {
            log.warn("Stripe is running in LIVE mode: real payments will be charged");
        }
        if (!properties.webhookConfigured()) {
            log.warn("STRIPE_WEBHOOK_SECRET is not set: payment webhooks will be rejected until it is configured");
        }
        return client;
    }
}