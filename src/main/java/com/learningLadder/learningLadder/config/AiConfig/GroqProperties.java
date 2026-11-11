package com.learningLadder.learningLadder.config.AiConfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.integrations.groq")
public record GroqProperties(

        @NotBlank(message = "app.integrations.groq.api-key is missing: set GROQ_API_KEY in your .env")
        String apiKey,

        @NotBlank
        @DefaultValue("https://api.groq.com/openai/v1")
        String baseUrl,

        @NotNull
        @DefaultValue("10s")
        Duration connectTimeout,

        @NotNull
        @DefaultValue("60s")
        Duration readTimeout

) {

    @Override
    public @NonNull String toString() {
        return "GroqProperties[baseUrl=" + baseUrl + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", apiKey=****]";
    }
}