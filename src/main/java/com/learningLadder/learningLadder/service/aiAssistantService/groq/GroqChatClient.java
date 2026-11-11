package com.learningLadder.learningLadder.service.aiAssistantService.groq;

import tools.jackson.databind.ObjectMapper;
import com.learningLadder.learningLadder.exception.groqException.GroqException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroqChatClient {

    private static final String CHAT_PATH = "/chat/completions";
    private static final int MAX_ATTEMPTS = 2;
    private static final long MAX_BACKOFF_MS = 5_000;

    private final RestClient groqRestClient;
    private final ObjectMapper objectMapper;

    public GroqCompletion complete(GroqChatRequest request) {
        GroqChatRequest body = request.withStream(false);
        long start = System.nanoTime();

        for (int attempt = 1; ; attempt++) {
            try {
                GroqChatResponse response = groqRestClient.post()
                        .uri(CHAT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GroqChatResponse.class);

                if (response == null || response.choices() == null || response.choices().isEmpty()) {
                    throw new GroqException("EMPTY_RESPONSE",
                            "The assistant did not return an answer. Please try again.", true, null);
                }
                GroqChatResponse.Choice choice = response.choices().get(0);
                String content = choice.message() == null || choice.message().content() == null
                        ? "" : choice.message().content();
                return new GroqCompletion(response.id(), response.model(), content,
                        choice.finishReason(), response.usage(), elapsedMs(start));

            } catch (RestClientResponseException ex) {
                GroqException mapped = mapHttpError(ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
                if (mapped.isRetryable() && attempt < MAX_ATTEMPTS) {
                    sleep(backoffMs(attempt, ex.getResponseHeaders() == null ? null
                            : ex.getResponseHeaders().getFirst("retry-after")));
                    continue;
                }
                throw mapped;
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_ATTEMPTS) {
                    sleep(backoffMs(attempt, null));
                    continue;
                }
                throw new GroqException("TIMEOUT",
                        "The assistant took too long to answer. Please try again.", true, ex);
            } catch (GroqException ex) {
                if (ex.isRetryable() && attempt < MAX_ATTEMPTS) {
                    sleep(backoffMs(attempt, null));
                    continue;
                }
                throw ex;
            }
        }
    }

    // =====================================================================
    // Streaming completion (Server-Sent Events from the provider)
    // =====================================================================
    /**
     * @param onDelta receives each new piece of text; it may throw to abort the stream
     *                (e.g. the student closed the page), which stops token usage immediately.
     */
    public GroqCompletion stream(GroqChatRequest request, Consumer<String> onDelta) {
        GroqChatRequest body = request.withStream(true);
        long start = System.nanoTime();

        try {
            return groqRestClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(body)
                    .exchange((clientRequest, response) -> {
                        int status = response.getStatusCode().value();
                        if (status >= 400) {
                            String errorBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw mapHttpError(status, errorBody, null);
                        }

                        StringBuilder content = new StringBuilder();
                        String id = null;
                        String model = null;
                        String finishReason = null;
                        GroqUsage usage = null;

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue; // blank keep-alive lines and comments
                                }
                                String data = line.substring(5).trim();
                                if (data.isEmpty()) {
                                    continue;
                                }
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                GroqStreamChunk chunk = objectMapper.readValue(data, GroqStreamChunk.class);
                                if (id == null) id = chunk.id();
                                if (model == null) model = chunk.model();
                                if (chunk.choices() != null) {
                                    for (GroqStreamChunk.Choice choice : chunk.choices()) {
                                        if (choice.delta() != null && choice.delta().content() != null
                                                && !choice.delta().content().isEmpty()) {
                                            content.append(choice.delta().content());
                                            onDelta.accept(choice.delta().content());
                                        }
                                        if (choice.finishReason() != null) {
                                            finishReason = choice.finishReason();
                                        }
                                    }
                                }
                                GroqUsage chunkUsage = chunk.resolvedUsage();
                                if (chunkUsage != null) {
                                    usage = chunkUsage;
                                }
                            }
                        }
                        return new GroqCompletion(id, model, content.toString(), finishReason, usage, elapsedMs(start));
                    });
        } catch (ResourceAccessException ex) {
            throw new GroqException("TIMEOUT",
                    "The connection to the assistant was interrupted. Please try again.", true, ex);
        }
    }

    // =====================================================================
    // Error translation
    // =====================================================================
    private GroqException mapHttpError(int status, String body, Throwable cause) {
        String safeBody = body == null ? "" : body.substring(0, Math.min(body.length(), 500));

        return switch (status) {
            case 401, 403 -> {
                log.error("Groq rejected the API key (HTTP {}). Check GROQ_API_KEY. Body: {}", status, safeBody);
                yield new GroqException("PROVIDER_AUTH_ERROR",
                        "The assistant is not available right now. Please contact Learning Ladder support.", false, cause);
            }
            case 404 -> {
                log.error("Groq model not found (HTTP 404). Check the assistant's model id. Body: {}", safeBody);
                yield new GroqException("MODEL_NOT_FOUND",
                        "This assistant is temporarily unavailable. Please try another assistant or contact support.", false, cause);
            }
            case 413 -> new GroqException("CONTEXT_TOO_LONG",
                    "This conversation has become too long. Please start a new conversation.", false, cause);
            case 429 -> {
                log.warn("Groq rate limit reached (HTTP 429). Body: {}", safeBody);
                yield new GroqException("RATE_LIMITED",
                        "The assistant is very busy right now. Please try again in a moment.", true, cause);
            }
            case 400, 422 -> {
                log.warn("Groq rejected the request (HTTP {}). Body: {}", status, safeBody);
                boolean tooLong = safeBody.toLowerCase().contains("context") || safeBody.toLowerCase().contains("too long");
                yield tooLong
                        ? new GroqException("CONTEXT_TOO_LONG",
                        "This conversation has become too long. Please start a new conversation.", false, cause)
                        : new GroqException("INVALID_REQUEST",
                        "The assistant could not process this message. Please rephrase and try again.", false, cause);
            }
            default -> {
                log.warn("Groq provider error (HTTP {}). Body: {}", status, safeBody);
                yield new GroqException("PROVIDER_ERROR",
                        "The assistant is having a temporary problem. Please try again shortly.", status >= 500, cause);
            }
        };
    }

    private long backoffMs(int attempt, String retryAfterHeader) {
        if (retryAfterHeader != null) {
            try {
                return Math.min((long) (Double.parseDouble(retryAfterHeader) * 1000), MAX_BACKOFF_MS);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.min(500L * (1L << (attempt - 1)), MAX_BACKOFF_MS);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GroqException("TIMEOUT", "The request was interrupted. Please try again.", false, ex);
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}