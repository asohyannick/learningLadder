package com.learningLadder.learningLadder.exception.internalServerException;

import lombok.Getter;

import java.io.Serial;

@Getter
public class InternalServerException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_MESSAGE = "Internal Server Error";
    private static final int DEFAULT_STATUS_CODE = 500;

    private final int statusCode;

    public InternalServerException() {
        super(DEFAULT_MESSAGE);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public InternalServerException(String message) {
        super(message);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public InternalServerException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

}