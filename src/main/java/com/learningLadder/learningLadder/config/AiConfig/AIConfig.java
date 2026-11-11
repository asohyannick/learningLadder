package com.learningLadder.learningLadder.config.AiConfig;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(GroqProperties.class)
public class AIConfig {

    @Bean("groqRestClient")
    public RestClient groqRestClient(GroqProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        log.info("Groq client configured: {}", properties);

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE + ", " + MediaType.TEXT_EVENT_STREAM_VALUE)
                .requestInterceptor(timingInterceptor())
                .build();
    }
    private ClientHttpRequestInterceptor timingInterceptor() {
        return (request, body, execution) -> {
            long start = System.nanoTime();
            var response = execution.execute(request, body);
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            log.debug("Groq {} {} -> {} ({} ms to first byte)",
                    request.getMethod(), request.getURI().getPath(), response.getStatusCode().value(), tookMs);
            return response;
        };
    }
}