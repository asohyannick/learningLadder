package com.learningLadder.learningLadder.config.ratingLimiting;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String KEY_PREFIX = "learningLadder:rate-limit:ip:";
    private static final int WINDOW_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> rateLimitScript;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long maxRequests;

    @SuppressWarnings("rawtypes")
    public RateLimitingFilter(StringRedisTemplate redisTemplate,
                              RedisScript<List> rateLimitScript,
                              ObjectMapper objectMapper,
                              @Value("${app.rate-limit.enabled:true}") boolean enabled,
                              @Value("${app.rate-limit.requests-per-minute:500}") long maxRequests) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !enabled
                || HttpMethod.OPTIONS.matches(request.getMethod())
                || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String key = KEY_PREFIX + request.getRemoteAddr();

        long count;
        long secondsToReset;
        try {
            List<?> result = redisTemplate.execute(rateLimitScript, List.of(key), String.valueOf(WINDOW_SECONDS));
            count = ((Number) result.get(0)).longValue();
            secondsToReset = ((Number) result.get(1)).longValue();
        } catch (Exception ex) {
            log.warn("Rate limiter unavailable, allowing request [ip={}]: {}", request.getRemoteAddr(), ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, maxRequests - count)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(secondsToReset));

        if (count > maxRequests) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondsToReset));
            writeTooManyRequests(request, response, secondsToReset);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response,
                                      long retryAfterSeconds) throws IOException {
        APIErrorResponse body = APIErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "Too many requests. Limit is " + maxRequests + " per minute. Try again in "
                        + retryAfterSeconds + " seconds.",
                request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}