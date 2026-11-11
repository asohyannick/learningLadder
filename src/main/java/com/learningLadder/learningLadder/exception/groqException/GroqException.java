package com.learningLadder.learningLadder.exception.groqException;

import lombok.Getter;

@Getter
public class GroqException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;
    private final boolean retryable;

    public GroqException(String errorCode, String userMessage, boolean retryable, Throwable cause) {
        super(errorCode + ": " + userMessage, cause);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.retryable = retryable;
    }
}