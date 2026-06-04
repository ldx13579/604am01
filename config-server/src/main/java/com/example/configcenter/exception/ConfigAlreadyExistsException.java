package com.example.configcenter.exception;

public class ConfigAlreadyExistsException extends RuntimeException {
    public ConfigAlreadyExistsException(String message) {
        super(message);
    }
}
