package com.learningLadder.learningLadder.exception.badRequestException;

public class BadRequestException extends RuntimeException {
    private static  final String message = "Bad Request exception";
    public BadRequestException(String message) {
        super(message);
    }
}
