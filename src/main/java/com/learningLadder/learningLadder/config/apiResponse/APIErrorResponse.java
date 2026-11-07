package com.learningLadder.learningLadder.config.apiResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record APIErrorResponse(
        boolean success,
        int status,
        String error,
        String errorCode,
        String message,
        String path,
        Instant timestamp,
        List<FieldValidationError> errors
) {

    public record FieldValidationError(String field, String message, Object rejectedValue) { }

    public static APIErrorResponse of(HttpStatus status, String errorCode, String message, String path) {
        return new APIErrorResponse(false, status.value(), status.getReasonPhrase(),
                errorCode, message, path, Instant.now(), null);
    }

    public static APIErrorResponse ofValidation(String message, String path,
                                                List<FieldValidationError> errors) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return new APIErrorResponse(false, status.value(), status.getReasonPhrase(),
                "VALIDATION_FAILED", message, path, Instant.now(), errors);
    }
}