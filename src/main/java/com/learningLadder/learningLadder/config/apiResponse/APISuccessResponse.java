package com.learningLadder.learningLadder.config.apiResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record APISuccessResponse<T>(
        boolean success,
        int status,
        String message,
        T data,
        String path,
        Instant timestamp
) {

    public static <T> APISuccessResponse<T> of(HttpStatus status, String message, T data, String path) {
        return new APISuccessResponse<>(true, status.value(), message, data, path, Instant.now());
    }

    public static <T> APISuccessResponse<T> ok(String message, T data, String path) {
        return of(HttpStatus.OK, message, data, path);
    }

    public static <T> APISuccessResponse<T> created(String message, T data, String path) {
        return of(HttpStatus.CREATED, message, data, path);
    }

    public static APISuccessResponse<Void> ok(String message, String path) {
        return of(HttpStatus.OK, message, null, path);
    }
}