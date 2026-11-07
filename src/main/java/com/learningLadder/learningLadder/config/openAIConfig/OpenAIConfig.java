package com.learningLadder.learningLadder.config.openAIConfig;

import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "app.integrations.groq")
public class OpenAIConfig {

    private String apiKey;
    private String baseUrl = "https://api.groq.com/openai/v1";

    private String model = "openai/gpt-oss-120b";
    private Double temperature = 0.3;
    private Integer maxTokens = 1024;

    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 60;

    @Bean("groqRestClient")
    public RestClient groqRestClient() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadRequestException("Groq API key is missing. Set GROQ_API_KEY.");
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}