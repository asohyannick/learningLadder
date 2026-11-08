package com.learningLadder.learningLadder.exception.emailDeliveryException;

import lombok.Getter;

import java.io.Serial;

@Getter
public class EmailDeliveryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_MESSAGE = "Bad Request Exception";
    private static final int DEFAULT_STATUS_CODE = 400;

    private final int statusCode;

    public EmailDeliveryException() {
        super(DEFAULT_MESSAGE);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public EmailDeliveryException(String message) {
        super(message);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public EmailDeliveryException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

}