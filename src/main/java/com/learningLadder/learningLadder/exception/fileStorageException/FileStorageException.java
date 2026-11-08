package com.learningLadder.learningLadder.exception.fileStorageException;

import lombok.Getter;

import java.io.Serial;

@Getter
public class FileStorageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_MESSAGE = "File Request Exception";
    private static final int DEFAULT_STATUS_CODE = 400;

    private final int statusCode;

    public FileStorageException() {
        super(DEFAULT_MESSAGE);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public FileStorageException(String message) {
        super(message);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public FileStorageException(Throwable cause) {
        super(DEFAULT_MESSAGE, cause);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = DEFAULT_STATUS_CODE;
    }

}