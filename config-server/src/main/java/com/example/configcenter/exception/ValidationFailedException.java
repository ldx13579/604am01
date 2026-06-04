package com.example.configcenter.exception;

public class ValidationFailedException extends RuntimeException {

    private final String message;

    public ValidationFailedException(String message) {
        super(message);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
