package com.learningLadder.learningLadder.config.resendConfig;

import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.resend.Resend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResendConfig {

    @Bean
    public Resend resendClient(
            @Value("${app.integrations.resend.api-key}")
            String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("Resend API key is missing. Set RESEND_API_KEY.");
        }
        return new Resend(apiKey);
    }
}